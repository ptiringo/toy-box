package com.example.api.infrastructure.studbook.horse

import com.example.api.domain.shared.UpdateConflict
import com.example.api.domain.shared.generateId
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorse
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseFixture
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseId
import com.example.api.domain.studbook.model.horse.bloodhorse.HorseName
import com.example.api.domain.studbook.model.horse.bloodhorse.Origin
import com.example.api.domain.studbook.model.horse.bloodhorse.PedigreeRegistrationNumber
import com.example.api.domain.studbook.model.horse.bloodhorse.Sex
import com.example.api.infrastructure.studbook.StudbookSeeder
import com.example.api.infrastructure.studbook.breeding.BreedingRegistrationSpringDataRepository
import com.example.api.infrastructure.studbook.inspection.HorseInspectionSpringDataRepository
import com.example.api.support.PostgresContainerSupport
import com.example.api.support.deleteAllStudbookTables
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import java.time.LocalDate
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
 * 8. save は集約の version（null なら insert、非 null なら楽観ロック付き update）で判別すること
 * 9. 古い version での save が UpdateConflict を返し先行の書き込みを保つこと（楽観ロック）
 * 10. 並行削除された集約への save が UpdateConflict を返すこと
 * 11. 馬名の一意性が UNIQUE 制約でスキーマ側にも強制されること（read-then-insert 競合の backstop）
 * 12. 血統登録番号の一意性が UNIQUE 制約でスキーマ側にも強制されること（read-then-insert 競合の backstop）
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestConstructor(autowireMode = AutowireMode.ALL)
class JdbcBloodHorseRepositoryContractTest(
    private val rows: BloodHorseSpringDataRepository,
    private val inspectionRows: HorseInspectionSpringDataRepository,
    private val registrationRows: BreedingRegistrationSpringDataRepository,
    private val jdbcClient: JdbcClient,
) : PostgresContainerSupport() {

    private val repository = JdbcBloodHorseRepository(rows)
    private val seeder = StudbookSeeder(inspectionRows, rows, registrationRows)

    @BeforeEach
    fun cleanUp() {
        deleteAllStudbookTables(jdbcClient)
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
            inspectionId = seeder.seedInspectionRow(),
            originType = "DOMESTIC",
            sireId = seeder.seedHorseRow(sex = "MALE"),
            damId = seeder.seedHorseRow(sex = "FEMALE"),
        )

    /** 内国産の父母を持つ命名済みの軽種馬を組み立てる（前提条件を満たす父=雄・母=雌・品種/ DNA 整合）。父母・仔馬自身の審査行は seed 済み。 */
    private fun namedDomesticFoal(): BloodHorse {
        val sire =
            seeder.seedHorse(
                BloodHorseFixture.bloodHorse(
                    sex = Sex.MALE,
                    registrationNumber = "SIRE-${generateId()}",
                )
            )
        val dam =
            seeder.seedHorse(
                BloodHorseFixture.bloodHorse(
                    sex = Sex.FEMALE,
                    registrationNumber = "DAM-${generateId()}",
                )
            )
        val foal =
            BloodHorse.create(
                    sire = sire,
                    dam = dam,
                    entry = BloodHorseFixture.studBookEntry(sex = Sex.MALE),
                    inspection = BloodHorseFixture.inspection(),
                    registrationNumber =
                        PedigreeRegistrationNumber.create("FOAL-${generateId()}").unwrap(),
                )
                .unwrap()
        seeder.seedInspectionFor(foal)
        return foal.assignName(HorseName.create("オグリキャップ").unwrap()).unwrap().aggregate
    }

    @Test
    fun `外部採番のIDを持つ新規行はversionがnullなのでinsertされる`() {
        val id = generateId()
        val saved = rows.save(domesticRow(id = id))

        assert(saved.id == id)
        assert(saved.version != null)
        // domesticRow() が FK 前提で seed する父・母の 2 行 + 対象行の 1 行 = 3 行
        assert(rows.count() == 3L)
        assert(rows.findById(id).isPresent)
    }

    @Test
    fun `既存行をupdateするとversionがインクリメントされる`() {
        val inserted = rows.save(domesticRow())

        val updated = rows.save(inserted.copy(coatColor = "CHESTNUT"))

        assert(updated.id == inserted.id)
        assert(updated.version!! > inserted.version!!)
        // domesticRow() が FK 前提で seed する父・母の 2 行 + 対象行の 1 行 = 3 行（update なので増減なし）
        assert(rows.count() == 3L)
        assert(rows.findById(inserted.id).orElseThrow().coatColor == "CHESTNUT")
    }

    @Test
    fun `輸入馬は出自Importedと未命名のまま往復できる`() {
        val imported = BloodHorseFixture.bloodHorse(sex = Sex.FEMALE)
        seeder.seedInspectionFor(imported)
        val expectedOrigin = imported.origin as Origin.Imported

        val saved = repository.save(imported).unwrap()
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

        repository.save(foal).unwrap()
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
        val importedFixture = BloodHorseFixture.bloodHorse(sex = Sex.FEMALE)
        seeder.seedInspectionFor(importedFixture)
        val imported = repository.save(importedFixture).unwrap()
        val foal = repository.save(namedDomesticFoal()).unwrap()
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
    fun `存在しない審査を参照する行はFK制約で拒否される`() {
        // マッパー／ユースケースは常に親を先に保存するが、FK（ADR-0053）が DB 単独でも
        // 壊れた参照の混入を拒否することを担保する。
        val orphan = domesticRow().copy(inspectionId = generateId())

        assertThrows<DataIntegrityViolationException> { rows.save(orphan) }
    }

    @Test
    fun `存在しないIDのfindByIdはnullを返す`() {
        assert(repository.findById(BloodHorseId(generateId())) == null)
    }

    @Test
    fun `既に付与済みの馬名は existsByName が true を返す`() {
        repository.save(namedDomesticFoal()).unwrap() // "オグリキャップ" で命名済み

        assert(repository.existsByName(HorseName.create("オグリキャップ").unwrap()))
    }

    @Test
    fun `未使用の馬名は existsByName が false を返す`() {
        repository.save(namedDomesticFoal()).unwrap() // "オグリキャップ"

        assert(!repository.existsByName(HorseName.create("トウカイテイオー").unwrap()))
    }

    @Test
    fun `同一馬名の二重insertはUNIQUE制約で拒否される`() {
        // ドメインサービス nameHorse の existsByName 検証をすり抜ける
        // read-then-insert 並行競合（#532）の backstop。
        repository.save(namedDomesticFoal()).unwrap() // "オグリキャップ" で命名済み
        val duplicate = namedDomesticFoal() // 別個体・同じ馬名

        assertThrows<DataIntegrityViolationException> { repository.save(duplicate) }
    }

    @Test
    fun `保存済み集約の再saveはupdateになりversionが進む`() {
        val fixture = BloodHorseFixture.bloodHorse()
        seeder.seedInspectionFor(fixture)
        val inserted = repository.save(fixture).unwrap()
        assert(inserted.version != null)

        val named = inserted.assignName(HorseName.create("オグリキャップ").unwrap()).unwrap().aggregate
        val updated = repository.save(named).unwrap()

        assert(updated.version!! > inserted.version!!)
        assert(rows.count() == 1L)
        assert(repository.findById(inserted.id)?.name?.value == "オグリキャップ")
    }

    @Test
    fun `古いversionでのsaveはUpdateConflictを返し先行の書き込みが保たれる`() {
        val fixture = BloodHorseFixture.bloodHorse()
        seeder.seedInspectionFor(fixture)
        val inserted = repository.save(fixture).unwrap()
        repository
            .save(inserted.assignName(HorseName.create("オグリキャップ").unwrap()).unwrap().aggregate)
            .unwrap()

        val conflicted =
            repository.save(
                inserted.assignName(HorseName.create("トウカイテイオー").unwrap()).unwrap().aggregate
            )

        assert(conflicted.getError() == UpdateConflict)
        assert(repository.findById(inserted.id)?.name?.value == "オグリキャップ")
    }

    @Test
    fun `並行削除された保存済み集約のsaveはUpdateConflictを返す`() {
        val fixture = BloodHorseFixture.bloodHorse()
        seeder.seedInspectionFor(fixture)
        val inserted = repository.save(fixture).unwrap()
        rows.deleteAll()

        val conflicted =
            repository.save(
                inserted.assignName(HorseName.create("オグリキャップ").unwrap()).unwrap().aggregate
            )

        assert(conflicted.getError() == UpdateConflict)
    }

    @Test
    fun `既に保存済みの登録番号は existsByRegistrationNumber が true を返す`() {
        val horse = BloodHorseFixture.bloodHorse() // registrationNumber = "2023104567"
        seeder.seedInspectionFor(horse)
        repository.save(horse).unwrap()

        assert(
            repository.existsByRegistrationNumber(
                PedigreeRegistrationNumber.create("2023104567").unwrap()
            )
        )
    }

    @Test
    fun `未使用の登録番号は existsByRegistrationNumber が false を返す`() {
        val horse = BloodHorseFixture.bloodHorse() // registrationNumber = "2023104567"
        seeder.seedInspectionFor(horse)
        repository.save(horse).unwrap()

        assert(
            !repository.existsByRegistrationNumber(
                PedigreeRegistrationNumber.create("9999999999").unwrap()
            )
        )
    }

    @Test
    fun `同一血統登録番号の二重insertはUNIQUE制約で拒否される`() {
        // ドメインサービス ensurePedigreeRegistrationNumberAvailable の検証をすり抜ける
        // read-then-insert 並行競合（#532）の backstop。
        val first = BloodHorseFixture.bloodHorse(registrationNumber = "DUP-2024-0001")
        seeder.seedInspectionFor(first)
        repository.save(first).unwrap()

        val duplicate = BloodHorseFixture.bloodHorse(registrationNumber = "DUP-2024-0001")
        seeder.seedInspectionFor(duplicate)

        assertThrows<DataIntegrityViolationException> { repository.save(duplicate) }
    }
}
