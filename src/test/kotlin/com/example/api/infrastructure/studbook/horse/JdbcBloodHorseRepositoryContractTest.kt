package com.example.api.infrastructure.studbook.horse

import com.example.api.domain.shared.generateId
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorse
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseFixture
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseId
import com.example.api.domain.studbook.model.horse.bloodhorse.HorseName
import com.example.api.domain.studbook.model.horse.bloodhorse.Origin
import com.example.api.domain.studbook.model.horse.bloodhorse.PedigreeRegistrationNumber
import com.example.api.domain.studbook.model.horse.bloodhorse.Sex
import com.example.api.support.PostgresContainerSupport
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
 * ドメインポート BloodHorseRepository の Spring Data JDBC 実装 [JdbcBloodHorseRepository] の契約テスト （ADR-0027 /
 * ADR-0030 / #435）。
 *
 * 本番ターゲットと同じ PostgreSQL（Testcontainers、[PostgresContainerSupport] で共有）に対して検証する。スキーマは
 * マイグレーション（`db/migration/V*.sql`）を Flyway が起動時に適用して用意する。
 *
 * 検証する契約:
 * 1. value class ID・各種 VO・enum を、永続化モデル分離＋手書きマッパーで橋渡しできること
 * 2. 外部採番（UUIDv7）で `@Id` が常に非 null でも、`@Version` が null のとき insert と判定されること（落とし穴②）
 * 3. 既存行の update で `@Version` がインクリメントされること（楽観ロック兼用。落とし穴③）
 * 4. イミュータブル集約 [BloodHorse] を ID を保ったまま再構成（reconstitute）して往復できること
 * 5. sealed な出自 [Origin]（内国産＝父母ID／輸入＝原産国・揚陸日）が判別子フラット化を経て双方往復できること
 * 6. 馬名（[HorseName]）の命名済み／未命名の双方が往復できること
 * 7. [BloodHorseRepository.findAllById] が複数IDをまとめて引き当てられること
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestConstructor(autowireMode = AutowireMode.ALL)
class JdbcBloodHorseRepositoryContractTest(private val rows: BloodHorseSpringDataRepository) :
    PostgresContainerSupport() {

    private val repository = JdbcBloodHorseRepository(rows)

    @BeforeEach
    fun cleanUp() {
        rows.deleteAll()
    }

    private fun domesticRow(id: UUID = generateId()) =
        BloodHorseRow(
            id = id,
            registrationNumber = "2023104567",
            sex = Sex.MALE.name,
            coatColor = "BAY",
            breedType = "THOROUGHBRED",
            dateOfBirth = LocalDate.of(2023, 3, 15),
            breeder = "ノーザンファーム",
            microchipNumber = "392140000000001",
            originType = "DOMESTIC",
            sireId = generateId(),
            damId = generateId(),
        )

    /** 内国産の父母を持つ命名済みの軽種馬を組み立てる（前提条件を満たす父=雄・母=雌・品種/ DNA 整合）。 */
    private fun namedDomesticFoal(): BloodHorse {
        val sire = BloodHorseFixture.bloodHorse(sex = Sex.MALE)
        val dam = BloodHorseFixture.bloodHorse(sex = Sex.FEMALE)
        val foal =
            BloodHorse.create(
                    sire = sire,
                    dam = dam,
                    entry = BloodHorseFixture.studBookEntry(sex = Sex.MALE),
                    registrationNumber = PedigreeRegistrationNumber.create("2023109999").unwrap(),
                )
                .unwrap()
        return foal.assignName(HorseName.create("オグリキャップ").unwrap()).unwrap().aggregate
    }

    @Test
    fun `外部採番のIDを持つ新規行はversionがnullなのでinsertされる`() {
        val id = generateId()
        val saved = rows.save(domesticRow(id = id))

        assert(saved.id == id)
        assert(saved.version != null)
        assert(rows.count() == 1L)
        assert(rows.findById(id).isPresent)
    }

    @Test
    fun `既存行をupdateするとversionがインクリメントされる`() {
        val inserted = rows.save(domesticRow())

        val updated = rows.save(inserted.copy(coatColor = "CHESTNUT"))

        assert(updated.id == inserted.id)
        assert(updated.version!! > inserted.version!!)
        assert(rows.count() == 1L)
        assert(rows.findById(inserted.id).orElseThrow().coatColor == "CHESTNUT")
    }

    @Test
    fun `輸入馬は出自Importedと未命名のまま往復できる`() {
        val imported = BloodHorseFixture.bloodHorse(sex = Sex.FEMALE)
        val expectedOrigin = imported.origin as Origin.Imported

        val saved = repository.save(imported)
        val found = repository.findById(imported.id)

        assert(saved.id == imported.id)
        assert(found != null)
        assert(found!!.id == imported.id)
        assert(found.registrationNumber == imported.registrationNumber)
        assert(found.sex == Sex.FEMALE)
        // 未命名のまま保たれる
        assert(found.name == null)
        // 出自 Imported が原産国・揚陸日ごと往復する
        val origin = found.origin
        assert(origin is Origin.Imported)
        origin as Origin.Imported
        assert(origin.originCountry == expectedOrigin.originCountry)
        assert(origin.landingDate == expectedOrigin.landingDate)
    }

    @Test
    fun `内国産の命名済み馬は出自Domesticと馬名ごと往復できる`() {
        val foal = namedDomesticFoal()
        val expectedOrigin = foal.origin as Origin.Domestic

        repository.save(foal)
        val found = repository.findById(foal.id)

        assert(found != null)
        // 命名済みの馬名が往復する
        assert(found!!.name?.value == "オグリキャップ")
        // 出自 Domestic が父母IDごと往復する
        val origin = found.origin
        assert(origin is Origin.Domestic)
        origin as Origin.Domestic
        assert(origin.sireId == expectedOrigin.sireId)
        assert(origin.damId == expectedOrigin.damId)
    }

    @Test
    fun `findAllByIdはヒットしたIDだけをまとめて返す`() {
        val imported = repository.save(BloodHorseFixture.bloodHorse(sex = Sex.FEMALE))
        val foal = repository.save(namedDomesticFoal())
        val missing = BloodHorseId(generateId())

        val found = repository.findAllById(setOf(imported.id, foal.id, missing))

        assert(found.size == 2)
        assert(found[imported.id]?.id == imported.id)
        assert(found[foal.id]?.id == foal.id)
        // 存在しないIDはキーに現れない
        assert(missing !in found)
    }

    @Test
    fun `出自の不変条件に反する行はCHECK制約で拒否される`() {
        // DOMESTIC を名乗りつつ父IDが欠落（かつ非該当列もない）＝ sealed Origin の不変条件違反。
        // マッパーは常に整合した行しか作らないが、スキーマ側の CHECK 制約（chk_blood_horse_origin）が
        // DB 単独でもこの不正な組合せを拒否することを担保する。
        val inconsistent = domesticRow().copy(sireId = null)

        assertThrows<DataIntegrityViolationException> { rows.save(inconsistent) }
    }

    @Test
    fun `存在しないIDのfindByIdはnullを返す`() {
        assert(repository.findById(BloodHorseId(generateId())) == null)
    }
}
