package com.example.api.infrastructure.studbook.breeding

import com.example.api.domain.shared.generateId
import com.example.api.domain.studbook.model.breeding.BreedingRegistration
import com.example.api.domain.studbook.model.breeding.BreedingRegistrationId
import com.example.api.domain.studbook.model.breeding.BreedingRegistrationNumber
import com.example.api.domain.studbook.model.breeding.BreedingRole
import com.example.api.domain.studbook.model.breeding.RetirementReason
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseFixture
import com.example.api.domain.studbook.model.horse.bloodhorse.Sex
import com.example.api.support.PostgresContainerSupport
import com.github.michaelbull.result.unwrap
import java.time.LocalDate
import java.util.UUID
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
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
            role = BreedingRole.STALLION.name,
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

        val saved = repository.save(registration)
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

        repository.save(retired)
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
}
