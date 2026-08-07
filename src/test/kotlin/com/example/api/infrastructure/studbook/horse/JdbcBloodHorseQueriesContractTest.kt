package com.example.api.infrastructure.studbook.horse

import com.example.api.domain.shared.WorldId
import com.example.api.domain.shared.generateId
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseId
import com.example.api.domain.studbook.model.horse.bloodhorse.BreedType
import com.example.api.domain.studbook.model.horse.bloodhorse.CoatColor
import com.example.api.domain.studbook.model.horse.bloodhorse.Origin
import com.example.api.domain.studbook.model.horse.bloodhorse.Sex
import com.example.api.infrastructure.studbook.StudbookSeeder
import com.example.api.infrastructure.studbook.breeding.BreedingRegistrationSpringDataRepository
import com.example.api.infrastructure.studbook.inspection.HorseInspectionSpringDataRepository
import com.example.api.support.PostgresContainerSupport
import java.time.LocalDate
import java.util.UUID
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.context.TestConstructor
import org.springframework.test.context.TestConstructor.AutowireMode

/**
 * 読み取りポート [com.example.api.application.studbook.horse.BloodHorseQueries] の JDBC 実装
 * [JdbcBloodHorseQueries] の契約テスト（軽量 CQRS / L2。ADR-0031）。
 *
 * 本番ターゲットの PostgreSQL（Testcontainers、[PostgresContainerSupport] で共有）に対し、 全件・id
 * 昇順・列マッピングが正しいかを検証する。行の投入は [StudbookSeeder] の row 系で行う。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestConstructor(autowireMode = AutowireMode.ALL)
class JdbcBloodHorseQueriesContractTest(
    private val jdbcClient: JdbcClient,
    private val horseRows: BloodHorseSpringDataRepository,
    private val inspectionRows: HorseInspectionSpringDataRepository,
    private val registrationRows: BreedingRegistrationSpringDataRepository,
) : PostgresContainerSupport() {

    private val queries = JdbcBloodHorseQueries(jdbcClient)
    // WorldId は value class で lateinit を付けられないため、生 UUID を保持して都度包む
    private lateinit var worldIdValue: UUID
    private val worldId
        get() = WorldId(worldIdValue)

    private lateinit var seeder: StudbookSeeder

    /** 基底クラスの TRUNCATE（@BeforeEach）の後に世界を作る必要があるため、フィールド初期化ではなくここで組む。 */
    @BeforeEach
    fun setUpWorld() {
        worldIdValue = createWorld()
        seeder = StudbookSeeder(worldId, inspectionRows, horseRows, registrationRows)
    }

    @Test
    fun `登録済みの馬を全件 id 昇順で返す`() {
        // generateId() は同一ミリ秒内での単調増加を保証しない（ADR-0005 は「ほぼ単調」＝インデックス局所性が目的）
        // ため、大小が自明な固定 ID を使う。さらに挿入順を id 昇順と逆にして、ORDER BY id が効いていることを検証する。
        val smaller = UUID.fromString("00000000-0000-7000-8000-000000000001")
        val larger = UUID.fromString("00000000-0000-7000-8000-000000000002")
        seeder.seedHorseRow(id = larger, registrationNumber = "REG-002")
        seeder.seedHorseRow(id = smaller, registrationNumber = "REG-001")

        val views = queries.findAll(worldId)

        assert(views.map { it.id } == listOf(smaller, larger))
        val head = views.first()
        assert(head.registrationNumber == "REG-001")
        // seedHorseRow のデフォルト（coat_color=BAY / breed_type=THOROUGHBRED / name 未設定）を写せていること
        assert(head.coatColor == CoatColor.BAY)
        assert(head.name == null)
    }

    @Test
    fun `1頭も登録が無ければ空リストを返す`() {
        assert(queries.findAll(worldId).isEmpty())
    }

    @Test
    fun `輸入馬を ID で引き審査のマイクロチップ番号ごと詰めて返す`() {
        val id = seeder.seedHorseRow(registrationNumber = "REG-101")

        val view = queries.findById(worldId, BloodHorseId(id))

        assert(view != null)
        assert(view!!.id == id)
        assert(view.registrationNumber == "REG-101")
        assert(view.sex == Sex.MALE)
        assert(view.coatColor == CoatColor.BAY)
        assert(view.breedType == BreedType.THOROUGHBRED)
        assert(view.breeder == "Coolmore")
        assert(view.name == null)
        // マイクロチップ番号は blood_horse ではなく horse_inspection 側の列。JOIN で引けていること。
        assert(view.microchipNumber == "392140000000001")
        // seedHorseRow の既定は輸入馬（origin_type = IMPORTED）。
        val origin = view.origin
        assert(origin is Origin.Imported)
        assert((origin as Origin.Imported).originCountry.name == "アイルランド")
        assert(origin.landingDate.value == LocalDate.of(2024, 9, 1))
    }

    @Test
    fun `内国産馬を ID で引くと父母 ID を持つ Domestic として復元する`() {
        val sireId = seeder.seedHorseRow(sex = "MALE", registrationNumber = "REG-102")
        val damId = seeder.seedHorseRow(sex = "FEMALE", registrationNumber = "REG-103")
        val id = generateId()
        seedDomesticHorseRow(id = id, sireId = sireId, damId = damId)

        val view = queries.findById(worldId, BloodHorseId(id))

        assert(view != null)
        val origin = view!!.origin
        assert(origin is Origin.Domestic)
        assert((origin as Origin.Domestic).sireId == BloodHorseId(sireId))
        assert(origin.damId == BloodHorseId(damId))
    }

    @Test
    fun `移行取り込み馬を ID で引くと CarriedOver として復元する`() {
        val id = generateId()
        seedCarriedOverHorseRow(id = id)

        val view = queries.findById(worldId, BloodHorseId(id))

        assert(view != null)
        assert(view!!.origin == Origin.CarriedOver)
    }

    @Test
    fun `存在しない ID なら null を返す`() {
        assert(queries.findById(worldId, BloodHorseId(generateId())) == null)
    }

    /** 内国産（origin_type = DOMESTIC）の馬行を審査行ごと作る。父母行は呼び出し側で先に seed しておくこと。 */
    private fun seedDomesticHorseRow(id: UUID, sireId: UUID, damId: UUID) {
        horseRows.save(
            BloodHorseRow(
                worldId = worldId.value,
                id = id,
                registrationNumber = "REG-104",
                sex = "MALE",
                coatColor = "BAY",
                breedType = "THOROUGHBRED",
                dateOfBirth = LocalDate.of(2023, 4, 10),
                breeder = "ノーザンファーム",
                inspectionId = seeder.seedInspectionRow(),
                originType = "DOMESTIC",
                sireId = sireId,
                damId = damId,
            )
        )
    }

    /** 移行取り込み（origin_type = CARRIED_OVER）の馬行を審査行ごと作る。バリアント固有列はすべて NULL。 */
    private fun seedCarriedOverHorseRow(id: UUID) {
        horseRows.save(
            BloodHorseRow(
                worldId = worldId.value,
                id = id,
                registrationNumber = "REG-105",
                sex = "FEMALE",
                coatColor = "BAY",
                breedType = "THOROUGHBRED",
                dateOfBirth = LocalDate.of(2015, 3, 20),
                breeder = "社台ファーム",
                inspectionId = seeder.seedInspectionRow(),
                originType = "CARRIED_OVER",
            )
        )
    }
}
