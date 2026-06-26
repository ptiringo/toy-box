package com.example.api.infrastructure.studbook.breeding

import com.example.api.domain.shared.generateId
import com.example.api.domain.studbook.model.breeding.BreedingFixture
import com.example.api.domain.studbook.model.breeding.BreedingResultId
import com.example.api.domain.studbook.model.breeding.FoalingOutcome
import com.example.api.support.PostgresContainerSupport
import com.github.michaelbull.result.unwrap
import java.time.LocalDate
import java.time.Year
import java.util.UUID
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
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
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestConstructor(autowireMode = AutowireMode.ALL)
class JdbcBreedingResultRepositoryContractTest(
    private val rows: BreedingResultSpringDataRepository
) : PostgresContainerSupport() {

    private val repository = JdbcBreedingResultRepository(rows)

    @BeforeEach
    fun cleanUp() {
        rows.deleteAll()
    }

    /** 種付せず（covering 全 NULL・区分 NOT_COVERED）の整合した行。CHECK 制約を満たす最小行。 */
    private fun uncoveredRow(id: UUID = generateId()) =
        BreedingResultRow(
            id = id,
            breedingRegistrationId = generateId(),
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
        val result = BreedingFixture.breedingResult()

        val saved = repository.save(result)
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
            BreedingFixture.breedingResult()
                .recordFoaling(FoalingOutcome.LiveFoal(foalingDate))
                .unwrap()

        repository.save(reported)
        val found = repository.findById(reported.id)

        assert(found != null)
        assert(found!!.covering != null)
        // 生産（LiveFoal）は判別子＋分娩日で往復する
        assert(found.outcome == FoalingOutcome.LiveFoal(foalingDate))
    }

    @Test
    fun `産駒なし区分を報告した成績は区分が往復し分娩日を持たない`() {
        val reported =
            BreedingFixture.breedingResult().recordFoaling(FoalingOutcome.NotConceived).unwrap()

        repository.save(reported)
        val found = repository.findById(reported.id)

        assert(found != null)
        assert(found!!.outcome == FoalingOutcome.NotConceived)
    }

    @Test
    fun `種付せずの成績は種付なし区分NotCoveredのまま往復できる`() {
        val uncovered = BreedingFixture.uncoveredBreedingResult()

        repository.save(uncovered)
        val found = repository.findById(uncovered.id)

        assert(found != null)
        // 種付なし＝covering は null、区分は NotCovered で確定
        assert(found!!.covering == null)
        assert(found.outcome == FoalingOutcome.NotCovered)
    }

    @Test
    fun `findByBreedingRegistrationIdAndBreedingYearで繁殖牝馬と年から引き当てられる`() {
        val result = BreedingFixture.breedingResult() // breedingYear=2024
        repository.save(result)

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
    fun `存在しないIDのfindByIdはnullを返す`() {
        assert(repository.findById(BreedingResultId(generateId())) == null)
    }
}
