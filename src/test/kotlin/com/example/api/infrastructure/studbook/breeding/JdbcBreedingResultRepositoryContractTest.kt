package com.example.api.infrastructure.studbook.breeding

import com.example.api.domain.shared.UpdateConflict
import com.example.api.domain.shared.generateId
import com.example.api.domain.studbook.model.breeding.BreedingFixture
import com.example.api.domain.studbook.model.breeding.BreedingResult
import com.example.api.domain.studbook.model.breeding.BreedingResultId
import com.example.api.domain.studbook.model.breeding.FoalingOutcome
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseFixture
import com.example.api.domain.studbook.model.horse.bloodhorse.Sex
import com.example.api.infrastructure.studbook.StudbookSeeder
import com.example.api.infrastructure.studbook.horse.BloodHorseSpringDataRepository
import com.example.api.infrastructure.studbook.inspection.HorseInspectionSpringDataRepository
import com.example.api.support.PostgresContainerSupport
import com.example.api.support.deleteAllStudbookTables
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import java.time.LocalDate
import java.time.Year
import java.util.UUID
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.context.TestConstructor
import org.springframework.test.context.TestConstructor.AutowireMode

/**
 * ドメインポート BreedingResultRepository の Spring Data JDBC 実装 [JdbcBreedingResultRepository] の契約テスト
 * （ADR-0027 / ADR-0030 / #435）。
 *
 * 本番ターゲットと同じ PostgreSQL（Testcontainers、[PostgresContainerSupport] で共有）に対して検証する。スキーマは
 * マイグレーション（`db/migration/V*.sql`）を Flyway が起動時に適用して用意する。
 *
 * 検証する契約:
 * 1. value class ID・各種 VO を、永続化モデル分離＋手書きマッパーで橋渡しできること
 * 2. 外部採番（UUIDv7）で `@Id` が常に非 null でも、`@Version` が null のとき insert と判定されること（落とし穴②）
 * 3. 既存行の update で `@Version` がインクリメントされること（楽観ロック兼用。落とし穴③）
 * 4. イミュータブル集約 [BreedingResult] を ID を保ったまま再構成（reconstitute）して往復できること
 * 5. nullable な種付（`Covering`）の種付あり／種付せずの双方が往復できること
 * 6. sealed な分娩結果（`FoalingOutcome`）の未報告・生産（分娩日あり）・産駒なし区分が往復できること
 * 7. `findByBreedingRegistrationIdAndBreedingYear` が繁殖牝馬×繁殖年で引き当てられること
 * 8. covering と区分の整合（不変条件）が CHECK 制約でスキーマ側にも強制されること
 * 9. save は集約の version（null なら insert、非 null なら楽観ロック付き update）で判別すること
 * 10. 古い version での save が UpdateConflict を返し先行の書き込みを保つこと（楽観ロック）
 * 11. 並行削除された集約への save が UpdateConflict を返すこと
 * 12. 「繁殖登録×繁殖年」の一意性が UNIQUE 制約でスキーマ側にも強制されること（read-then-insert 競合の backstop）
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestConstructor(autowireMode = AutowireMode.ALL)
class JdbcBreedingResultRepositoryContractTest(
    private val rows: BreedingResultSpringDataRepository,
    private val inspectionRows: HorseInspectionSpringDataRepository,
    private val horseRows: BloodHorseSpringDataRepository,
    private val registrationRows: BreedingRegistrationSpringDataRepository,
    private val jdbcClient: JdbcClient,
) : PostgresContainerSupport() {

    private val repository = JdbcBreedingResultRepository(rows)
    private val seeder = StudbookSeeder(inspectionRows, horseRows, registrationRows)

    @BeforeEach
    fun cleanUp() {
        deleteAllStudbookTables(jdbcClient)
    }

    /** 親（繁殖牝馬の登録と種牡馬）を seed 済みの、分娩結果未報告の成績を組む。 */
    private fun seededBreedingResult(): BreedingResult {
        val broodmareRegistration =
            BreedingFixture.breedingRegistration(
                broodmare =
                    seeder.seedHorse(
                        BloodHorseFixture.bloodHorse(
                            sex = Sex.FEMALE,
                            registrationNumber = "MARE-${generateId()}",
                        )
                    )
            )
        val stallionRegistration =
            BreedingFixture.stallionRegistration(
                stallion =
                    seeder.seedHorse(
                        BloodHorseFixture.bloodHorse(
                            sex = Sex.MALE,
                            registrationNumber = "STALLION-${generateId()}",
                        )
                    )
            )
        seeder.seedRegistration(broodmareRegistration)
        seeder.seedRegistration(stallionRegistration)
        return BreedingFixture.breedingResult(
            broodmareRegistration = broodmareRegistration,
            stallionRegistration = stallionRegistration,
        )
    }

    /** 親（繁殖牝馬の登録）を seed 済みの、種付せず成績を組む。 */
    private fun seededUncoveredResult(): BreedingResult {
        val broodmareRegistration =
            BreedingFixture.breedingRegistration(
                broodmare = seeder.seedHorse(BloodHorseFixture.bloodHorse(sex = Sex.FEMALE))
            )
        seeder.seedRegistration(broodmareRegistration)
        return BreedingFixture.uncoveredBreedingResult(
            broodmareRegistration = broodmareRegistration
        )
    }

    /** 種付せず（covering 全 NULL・区分 NOT_COVERED）の整合した行。CHECK 制約を満たす最小行。 */
    private fun uncoveredRow(id: UUID = generateId()) =
        BreedingResultRow(
            id = id,
            breedingRegistrationId = seeder.seedRegistrationRow(),
            breedingYear = 2024,
            outcomeType = "NOT_COVERED",
        )

    @Test
    fun `外部採番のIDを持つ新規行はversionがnullなのでinsertされる`() {
        val id = generateId()
        val saved = rows.save(uncoveredRow(id = id))

        assert(saved.id == id)
        assert(saved.version != null)
        assert(rows.count() == 1L)
        assert(rows.findById(id).isPresent)
    }

    @Test
    fun `既存行をupdateするとversionがインクリメントされる`() {
        val inserted = rows.save(uncoveredRow())

        val updated = rows.save(inserted.copy(breedingYear = 2025))

        assert(updated.id == inserted.id)
        assert(updated.version!! > inserted.version!!)
        assert(rows.count() == 1L)
        assert(rows.findById(inserted.id).orElseThrow().breedingYear == 2025)
    }

    @Test
    fun `種付した未報告の成績は種付ごと往復し区分はnull`() {
        val result = seededBreedingResult()

        val saved = repository.save(result).unwrap()
        val found = repository.findById(result.id)

        assert(saved.id == result.id)
        assert(found != null)
        assert(found!!.id == result.id)
        assert(found.breedingYear == result.breedingYear)
        // 種付（Covering は data class なので構造等価）が往復する
        assert(found.covering != null)
        assert(found.covering == result.covering)
        // 未報告なので区分は null
        assert(found.outcome == null)
    }

    @Test
    fun `生産を報告した成績は分娩日ごと往復できる`() {
        val foalingDate = LocalDate.of(2025, 3, 1)
        val reported =
            seededBreedingResult().recordFoaling(FoalingOutcome.LiveFoal(foalingDate)).unwrap()

        repository.save(reported).unwrap()
        val found = repository.findById(reported.id)

        assert(found != null)
        assert(found!!.covering != null)
        // 生産（LiveFoal）は判別子＋分娩日で往復する
        assert(found.outcome == FoalingOutcome.LiveFoal(foalingDate))
    }

    @Test
    fun `産駒なし区分を報告した成績は区分が往復し分娩日を持たない`() {
        val reported = seededBreedingResult().recordFoaling(FoalingOutcome.NotConceived).unwrap()

        repository.save(reported).unwrap()
        val found = repository.findById(reported.id)

        assert(found != null)
        assert(found!!.outcome == FoalingOutcome.NotConceived)
    }

    @Test
    fun `種付せずの成績は種付なし区分NotCoveredのまま往復できる`() {
        val uncovered = seededUncoveredResult()

        repository.save(uncovered).unwrap()
        val found = repository.findById(uncovered.id)

        assert(found != null)
        // 種付なし＝covering は null、区分は NotCovered で確定
        assert(found!!.covering == null)
        assert(found.outcome == FoalingOutcome.NotCovered)
    }

    @Test
    fun `findByBreedingRegistrationIdAndBreedingYearで繁殖牝馬と年から引き当てられる`() {
        val result = seededBreedingResult() // breedingYear=2024
        repository.save(result).unwrap()

        val found =
            repository.findByBreedingRegistrationIdAndBreedingYear(
                result.breedingRegistrationId,
                Year.of(2024),
            )
        assert(found != null)
        assert(found!!.id == result.id)
        // 別の年は引き当たらない
        assert(
            repository.findByBreedingRegistrationIdAndBreedingYear(
                result.breedingRegistrationId,
                Year.of(2099),
            ) == null
        )
    }

    @Test
    fun `covering無しなのに区分がNotCovered以外の行はCHECK制約で拒否される`() {
        // covering_date が NULL（種付なし）なのに区分が NOT_COVERED 以外＝ BreedingResult の不変条件違反。
        // マッパーは常に整合した行しか作らないが、CHECK 制約（chk_breeding_result_outcome_covering）が
        // DB 単独でもこの不正な組合せを拒否することを担保する。
        val inconsistent = uncoveredRow().copy(outcomeType = "ABORTION")

        assertThrows<DataIntegrityViolationException> { rows.save(inconsistent) }
    }

    @Test
    fun `存在しない繁殖登録を参照する行はFK制約で拒否される`() {
        val orphan = uncoveredRow().copy(breedingRegistrationId = generateId())

        assertThrows<DataIntegrityViolationException> { rows.save(orphan) }
    }

    @Test
    fun `同一繁殖登録×同一繁殖年の二重insertはUNIQUE制約で拒否される`() {
        // ドメインサービス recordCovering / recordUncovered の一意性検証をすり抜ける
        // read-then-insert 並行競合（#532）の backstop。
        val registrationId = seeder.seedRegistrationRow()
        rows.save(uncoveredRow().copy(breedingRegistrationId = registrationId))
        val duplicate = uncoveredRow().copy(breedingRegistrationId = registrationId)

        assertThrows<DataIntegrityViolationException> { rows.save(duplicate) }
    }

    @Test
    fun `存在しないIDのfindByIdはnullを返す`() {
        assert(repository.findById(BreedingResultId(generateId())) == null)
    }

    @Test
    fun `保存済み集約の再saveはupdateになりversionが進む`() {
        val inserted = repository.save(seededBreedingResult()).unwrap()
        assert(inserted.version != null)

        val reported = inserted.recordFoaling(FoalingOutcome.NotConceived).unwrap()
        val updated = repository.save(reported).unwrap()

        assert(updated.version!! > inserted.version!!)
        assert(rows.count() == 1L)
        assert(repository.findById(inserted.id)?.outcome == FoalingOutcome.NotConceived)
    }

    @Test
    fun `古いversionでのsaveはUpdateConflictを返し先行の書き込みが保たれる`() {
        val inserted = repository.save(seededBreedingResult()).unwrap()
        repository.save(inserted.recordFoaling(FoalingOutcome.NotConceived).unwrap()).unwrap()

        val conflicted = repository.save(inserted.recordFoaling(FoalingOutcome.Abortion).unwrap())

        assert(conflicted.getError() == UpdateConflict)
        assert(repository.findById(inserted.id)?.outcome == FoalingOutcome.NotConceived)
    }

    @Test
    fun `並行削除された保存済み集約のsaveはUpdateConflictを返す`() {
        val inserted = repository.save(seededBreedingResult()).unwrap()
        rows.deleteAll()

        val conflicted =
            repository.save(inserted.recordFoaling(FoalingOutcome.NotConceived).unwrap())

        assert(conflicted.getError() == UpdateConflict)
    }

    @Test
    fun `提出済みの成績は提出日ごと往復できる`() {
        val submitted =
            seededBreedingResult()
                .recordFoaling(FoalingOutcome.LiveFoal(LocalDate.of(2025, 3, 1)))
                .unwrap()
                .submitReport(LocalDate.of(2025, 5, 30))
                .unwrap()

        repository.save(submitted).unwrap()
        val found = repository.findById(submitted.id)

        assert(found != null)
        assert(found!!.reportSubmittedOn == LocalDate.of(2025, 5, 30))
        assert(found.reportSubmittedLate == false)
    }

    @Test
    fun `未提出の成績はreport_submitted_onがnullで往復する`() {
        val unsubmitted = seededBreedingResult()

        repository.save(unsubmitted).unwrap()
        val found = repository.findById(unsubmitted.id)

        assert(found != null)
        assert(found!!.reportSubmittedOn == null)
        assert(found.reportSubmittedLate == null)
    }

    @Test
    fun `分娩結果未確定なのに提出日を持つ行はCHECK制約で拒否される`() {
        // 種付済み・分娩結果未報告（outcome_type IS NULL）なのに提出日がある不整合行
        val row =
            BreedingResultRow(
                id = generateId(),
                breedingRegistrationId = seeder.seedRegistrationRow(),
                breedingYear = 2024,
                coveringStallionId = seeder.seedHorseRow(sex = "MALE"),
                coveringDate = LocalDate.of(2024, 4, 1),
                coveringCertificateNumber = "C-2024-0001",
                outcomeType = null,
                reportSubmittedOn = LocalDate.of(2025, 5, 1),
            )

        assertThrows<DataIntegrityViolationException> { rows.save(row) }
    }
}
