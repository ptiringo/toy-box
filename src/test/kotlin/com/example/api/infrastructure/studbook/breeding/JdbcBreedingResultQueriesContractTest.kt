package com.example.api.infrastructure.studbook.breeding

import com.example.api.domain.shared.WorldId
import com.example.api.domain.shared.generateId
import com.example.api.domain.studbook.model.breeding.BreedingResultId
import com.example.api.domain.studbook.model.breeding.FoalingOutcome
import com.example.api.infrastructure.studbook.StudbookSeeder
import com.example.api.infrastructure.studbook.horse.BloodHorseSpringDataRepository
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
 * 読み取りポート [com.example.api.application.studbook.breeding.BreedingResultQueries] の JDBC 実装
 * [JdbcBreedingResultQueries] の契約テスト（軽量 CQRS / L2。ADR-0031）。
 *
 * 本番ターゲットの PostgreSQL（Testcontainers、[PostgresContainerSupport] で共有）に対し、フラット化した種付列・
 * 判別子からの分娩結果復元・提出列の写しと、世界スコープ（`world_id` の絞り込み）を検証する。行の投入は infrastructure 内部の
 * [BreedingResultSpringDataRepository] で行い、読み取りは別経路の実装で引く。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestConstructor(autowireMode = AutowireMode.ALL)
class JdbcBreedingResultQueriesContractTest(
    private val jdbcClient: JdbcClient,
    private val rows: BreedingResultSpringDataRepository,
    private val inspectionRows: HorseInspectionSpringDataRepository,
    private val horseRows: BloodHorseSpringDataRepository,
    private val registrationRows: BreedingRegistrationSpringDataRepository,
) : PostgresContainerSupport() {

    private val queries = JdbcBreedingResultQueries(jdbcClient)
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

    /** 種付あり行を作って ID を返す。outcomeType=null は分娩結果未報告。 */
    @Suppress("LongParameterList") // フラット化した列を素の値で並べる seed。名前付き引数で呼ぶ前提
    private fun seedCoveredRow(
        stallionId: UUID = seeder.seedHorseRow(sex = "MALE"),
        year: Int = 2024,
        outcomeType: String? = null,
        foalingDate: LocalDate? = null,
        reportSubmittedOn: LocalDate? = null,
        seedWorldId: WorldId = worldId,
        registrationId: UUID = seeder.seedRegistrationRow(),
    ): UUID {
        val id = generateId()
        rows.save(
            BreedingResultRow(
                worldId = seedWorldId.value,
                id = id,
                breedingRegistrationId = registrationId,
                breedingYear = year,
                coveringStallionId = stallionId,
                coveringDate = LocalDate.of(year, 4, 1),
                coveringPlace = "北海道",
                coveringCertificateNumber = "C-$year-0001",
                outcomeType = outcomeType,
                outcomeFoalingDate = foalingDate,
                reportSubmittedOn = reportSubmittedOn,
            )
        )
        return id
    }

    @Test
    fun `種付済みで未報告の成績を ID で引き種付列を詰めて返す`() {
        val stallionId = seeder.seedHorseRow(sex = "MALE")
        val id = seedCoveredRow(stallionId = stallionId)

        val view = queries.findById(worldId, BreedingResultId(id))

        assert(view != null)
        assert(view!!.id == id)
        assert(view.breedingYear == 2024)
        assert(view.stallionId == stallionId)
        assert(view.coveringDate == LocalDate.of(2024, 4, 1))
        assert(view.coveringPlace == "北海道")
        assert(view.certificateNumber == "C-2024-0001")
        // 未報告・未提出は判別子列・提出列とも NULL。
        assert(view.outcome == null)
        assert(view.reportSubmittedOn == null)
        assert(view.reportSubmittedLate == null)
    }

    @Test
    fun `生産の成績は分娩日を持つ LiveFoal として復元する`() {
        val id = seedCoveredRow(outcomeType = "LIVE_FOAL", foalingDate = LocalDate.of(2025, 3, 20))

        val view = queries.findById(worldId, BreedingResultId(id))

        val outcome = view!!.outcome
        assert(outcome is FoalingOutcome.LiveFoal)
        assert((outcome as FoalingOutcome.LiveFoal).foalingDate == LocalDate.of(2025, 3, 20))
    }

    @Test
    fun `分娩日を伴わない全区分を判別子から data object へ復元する`() {
        // 判別子は区分ごとに別の文字列。全区分を 1 件ずつ引いて写しの取り違えを防ぐ
        // （種付せず NOT_COVERED だけは種付列を伴わないので下の専用ケースで見る）。
        val expectations =
            mapOf(
                "NOT_CONCEIVED" to FoalingOutcome.NotConceived,
                "ABORTION" to FoalingOutcome.Abortion,
                "TWIN_ABORTION" to FoalingOutcome.TwinAbortion,
                "STILLBIRTH" to FoalingOutcome.Stillbirth,
                "TWIN_STILLBIRTH" to FoalingOutcome.TwinStillbirth,
                "NEONATAL_DEATH" to FoalingOutcome.NeonatalDeath,
                "TWIN_NEONATAL_DEATH" to FoalingOutcome.TwinNeonatalDeath,
            )

        expectations.forEach { (outcomeType, expected) ->
            val id = seedCoveredRow(outcomeType = outcomeType)

            val view = queries.findById(worldId, BreedingResultId(id))

            assert(view!!.outcome == expected)
        }
    }

    @Test
    fun `種付せずの成績は種付列がすべて null で NotCovered を持つ`() {
        val id = generateId()
        rows.save(
            BreedingResultRow(
                worldId = worldId.value,
                id = id,
                breedingRegistrationId = seeder.seedRegistrationRow(),
                breedingYear = 2024,
                outcomeType = "NOT_COVERED",
            )
        )

        val view = queries.findById(worldId, BreedingResultId(id))

        assert(view != null)
        assert(view!!.stallionId == null)
        assert(view.coveringDate == null)
        assert(view.coveringPlace == null)
        assert(view.certificateNumber == null)
        assert(view.outcome == FoalingOutcome.NotCovered)
    }

    @Test
    fun `提出済みの成績は提出日を写し期限超過を導出する`() {
        // 繁殖年 2024 の提出期限は 2025-05-31。その翌日の提出は期限超過。
        val id =
            seedCoveredRow(
                outcomeType = "LIVE_FOAL",
                foalingDate = LocalDate.of(2025, 3, 20),
                reportSubmittedOn = LocalDate.of(2025, 6, 1),
            )

        val view = queries.findById(worldId, BreedingResultId(id))

        assert(view!!.reportSubmittedOn == LocalDate.of(2025, 6, 1))
        assert(view.reportSubmittedLate == true)
    }

    @Test
    fun `存在しない ID なら null を返す`() {
        assert(queries.findById(worldId, BreedingResultId(generateId())) == null)
    }

    @Test
    fun `他の世界の繁殖成績は引けない`() {
        val otherWorldId = WorldId(createWorld())
        val otherSeeder = StudbookSeeder(otherWorldId, inspectionRows, horseRows, registrationRows)
        val id =
            seedCoveredRow(
                stallionId = otherSeeder.seedHorseRow(sex = "MALE"),
                seedWorldId = otherWorldId,
                registrationId = otherSeeder.seedRegistrationRow(),
            )

        assert(queries.findById(worldId, BreedingResultId(id)) == null)
    }
}
