package com.example.api.infrastructure.studbook.breeding

import com.example.api.domain.shared.UpdateConflict
import com.example.api.domain.shared.generateId
import com.example.api.domain.studbook.model.breeding.BreedingRegistration
import com.example.api.domain.studbook.model.breeding.BreedingRegistrationId
import com.example.api.domain.studbook.model.breeding.BreedingRegistrationNumber
import com.example.api.domain.studbook.model.breeding.BreedingRole
import com.example.api.domain.studbook.model.breeding.RetirementReason
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseFixture
import com.example.api.domain.studbook.model.horse.bloodhorse.Sex
import com.example.api.support.PostgresContainerSupport
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import java.time.LocalDate
import java.util.UUID
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.test.context.TestConstructor
import org.springframework.test.context.TestConstructor.AutowireMode

/**
 * ドメインポート BreedingRegistrationRepository の Spring Data JDBC 実装 [JdbcBreedingRegistrationRepository]
 * の契約テスト（ADR-0027 / ADR-0030 / #435）。
 *
 * 本番ターゲットと同じ PostgreSQL（Testcontainers、[PostgresContainerSupport] で共有）に対して検証する。スキーマは
 * マイグレーション（`db/migration/V*.sql`）を Flyway が起動時に適用して用意する。
 *
 * 検証する契約:
 * 1. value class の各種 ID（`BreedingRegistrationId` / `BloodHorseId`）↔ DB `uuid` 列を、永続化モデル分離＋
 *    手書きマッパーで橋渡しできること
 * 2. 外部採番（UUIDv7）で `@Id` が常に非 null でも、`@Version` が null のとき insert と判定されること（落とし穴②）
 * 3. 既存行の update で `@Version` がインクリメントされること（楽観ロック兼用。落とし穴③）
 * 4. イミュータブル集約 [BreedingRegistration] を ID を保ったまま再構成（reconstitute）して往復できること
 * 5. nullable な供用停止（`BreedingRetirement`）の供用中／供用停止済みの双方が 2 列のフラット化を経て往復できること
 * 6. save は集約の version（null なら insert、非 null なら楽観ロック付き update）で判別すること
 * 7. 古い version での save が UpdateConflict を返し先行の書き込みを保つこと（楽観ロック）
 * 8. 供用停止の共在不変条件が CHECK 制約でスキーマ側にも強制されること
 * 9. 並行削除された集約への save が UpdateConflict を返すこと
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestConstructor(autowireMode = AutowireMode.ALL)
class JdbcBreedingRegistrationRepositoryContractTest(
    private val rows: BreedingRegistrationSpringDataRepository
) : PostgresContainerSupport() {

    private val repository = JdbcBreedingRegistrationRepository(rows)

    @BeforeEach
    fun cleanUp() {
        rows.deleteAll()
    }

    private fun row(
        id: UUID = generateId(),
        retirementReason: String? = null,
        retirementOccurredOn: LocalDate? = null,
    ) =
        BreedingRegistrationRow(
            id = id,
            registrationNumber = "B-0001",
            registeredHorseId = generateId(),
            breedingRole = BreedingRole.STALLION.name,
            retirementReason = retirementReason,
            retirementOccurredOn = retirementOccurredOn,
        )

    @Test
    fun `外部採番のIDを持つ新規行はversionがnullなのでinsertされる`() {
        val id = generateId()
        val saved = rows.save(row(id = id))

        assert(saved.id == id)
        assert(saved.version != null)
        assert(rows.count() == 1L)
        assert(rows.findById(id).isPresent)
    }

    @Test
    fun `既存行をupdateするとversionがインクリメントされる`() {
        val inserted = rows.save(row())

        val updated = rows.save(inserted.copy(registrationNumber = "B-0002"))

        assert(updated.id == inserted.id)
        assert(updated.version!! > inserted.version!!)
        assert(rows.count() == 1L)
        assert(rows.findById(inserted.id).orElseThrow().registrationNumber == "B-0002")
    }

    @Test
    fun `供用中のドメイン集約をsaveしfindByIdでID不変のまま再構成できる`() {
        val mare = BloodHorseFixture.bloodHorse(sex = Sex.FEMALE)
        val registration =
            BreedingRegistration.create(BreedingRegistrationNumber.create("B-1234").unwrap(), mare)

        val saved = repository.save(registration).unwrap()
        val found = repository.findById(registration.id)

        // value class ID が DB 往復しても保たれ、ID ベースの等価性で同一集約とみなせる
        assert(saved.id == registration.id)
        assert(found != null)
        assert(found == registration)
        assert(found!!.registrationNumber == registration.registrationNumber)
        assert(found.registeredHorseId == mare.id)
        // 雌馬の繁殖登録は繁殖牝馬ロール（性から定まる）
        assert(found.role == BreedingRole.BROODMARE)
        // 供用中なので供用停止は記録されていない
        assert(found.retirement == null)
        assert(!found.isRetired)
    }

    @Test
    fun `供用停止済みのドメイン集約は事由と発生日が往復で保たれる`() {
        val stallion = BloodHorseFixture.bloodHorse(sex = Sex.MALE)
        val occurredOn = LocalDate.of(2026, 4, 1)
        val number = BreedingRegistrationNumber.create("B-5678").unwrap()
        val retired =
            BreedingRegistration.create(number, stallion)
                .retire(RetirementReason.DEATH, occurredOn)
                .unwrap()

        repository.save(retired).unwrap()
        val found = repository.findById(retired.id)

        assert(found != null)
        assert(found!!.isRetired)
        // nullable な供用停止 VO が 2 列のフラット化を経ても事由・発生日を保つ
        assert(found.retirement?.reason == RetirementReason.DEATH)
        assert(found.retirement?.occurredOn == occurredOn)
        assert(found.role == BreedingRole.STALLION)
    }

    @Test
    fun `存在しないIDのfindByIdはnullを返す`() {
        assert(repository.findById(BreedingRegistrationId(generateId())) == null)
    }

    @Test
    fun `保存済み集約の再saveはupdateになりversionが進む`() {
        val mare = BloodHorseFixture.bloodHorse(sex = Sex.FEMALE)
        val inserted =
            repository
                .save(
                    BreedingRegistration.create(
                        BreedingRegistrationNumber.create("B-1234").unwrap(),
                        mare,
                    )
                )
                .unwrap()
        assert(inserted.version != null)

        val retired = inserted.retire(RetirementReason.DEATH, LocalDate.of(2026, 4, 1)).unwrap()
        val updated = repository.save(retired).unwrap()

        assert(updated.version!! > inserted.version!!)
        assert(rows.count() == 1L)
        assert(repository.findById(inserted.id)?.isRetired == true)
    }

    @Test
    fun `古いversionでのsaveはUpdateConflictを返し先行の書き込みが保たれる`() {
        val mare = BloodHorseFixture.bloodHorse(sex = Sex.FEMALE)
        val inserted =
            repository
                .save(
                    BreedingRegistration.create(
                        BreedingRegistrationNumber.create("B-1234").unwrap(),
                        mare,
                    )
                )
                .unwrap()
        repository
            .save(inserted.retire(RetirementReason.DEATH, LocalDate.of(2026, 4, 1)).unwrap())
            .unwrap()

        val conflicted =
            repository.save(
                inserted.retire(RetirementReason.USE_CHANGE, LocalDate.of(2026, 5, 1)).unwrap()
            )

        assert(conflicted.getError() == UpdateConflict)
        assert(repository.findById(inserted.id)?.retirement?.reason == RetirementReason.DEATH)
    }

    @Test
    fun `並行削除された保存済み集約のsaveはUpdateConflictを返す`() {
        val mare = BloodHorseFixture.bloodHorse(sex = Sex.FEMALE)
        val inserted =
            repository
                .save(
                    BreedingRegistration.create(
                        BreedingRegistrationNumber.create("B-1234").unwrap(),
                        mare,
                    )
                )
                .unwrap()
        rows.deleteAll()

        val conflicted =
            repository.save(
                inserted.retire(RetirementReason.DEATH, LocalDate.of(2026, 4, 1)).unwrap()
            )

        assert(conflicted.getError() == UpdateConflict)
    }

    @Test
    fun `供用停止の共在不変条件に反する行はCHECK制約で拒否される`() {
        // 事由だけ在って発生日が欠落＝共在 VO（BreedingRetirement）の「両方 NULL か両方 NOT NULL」違反。
        // マッパーは常に整合した行しか作らないが、スキーマ側の CHECK 制約
        // （chk_breeding_registration_retirement_coexistence）が DB 単独でもこの不正な組合せを拒否することを担保する。
        val inconsistent =
            row(retirementReason = RetirementReason.DEATH.name, retirementOccurredOn = null)

        assertThrows<DataIntegrityViolationException> { rows.save(inconsistent) }
    }
}
