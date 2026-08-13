package com.example.api.infrastructure.studbook.breeding

import com.example.api.domain.shared.WorldId
import com.example.api.domain.shared.generateId
import com.example.api.domain.studbook.model.breeding.CoveringReportId
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
 * 読み取りポート [com.example.api.application.studbook.breeding.CoveringReportQueries] の JDBC 実装
 * [JdbcCoveringReportQueries] の契約テスト（軽量 CQRS / L2。ADR-0031）。
 *
 * 本番ターゲットの PostgreSQL（Testcontainers、[PostgresContainerSupport] で共有）に対し、列マッピングと 世界スコープ（`world_id`
 * の絞り込み）を検証する。行の投入は infrastructure 内部の [CoveringReportSpringDataRepository] で行い、読み取りは別経路の実装で引く。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestConstructor(autowireMode = AutowireMode.ALL)
class JdbcCoveringReportQueriesContractTest(
    private val jdbcClient: JdbcClient,
    private val rows: CoveringReportSpringDataRepository,
    private val inspectionRows: HorseInspectionSpringDataRepository,
    private val horseRows: BloodHorseSpringDataRepository,
    private val registrationRows: BreedingRegistrationSpringDataRepository,
) : PostgresContainerSupport() {

    private val queries = JdbcCoveringReportQueries(jdbcClient)
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

    /** 種付成績報告の行を種牡馬の繁殖登録ごと作って ID を返す。 */
    private fun seedReportRow(
        submittedOn: LocalDate = LocalDate.of(2024, 9, 30),
        seedWorldId: WorldId = worldId,
        registrationId: UUID = seeder.seedRegistrationRow(role = "STALLION"),
    ): UUID {
        val id = generateId()
        rows.save(
            CoveringReportRow(
                worldId = seedWorldId.value,
                id = id,
                stallionBreedingRegistrationId = registrationId,
                coveringYear = 2024,
                submittedOn = submittedOn,
            )
        )
        return id
    }

    @Test
    fun `提出済みの報告を ID で引き列を詰めて返す`() {
        val registrationId = seeder.seedRegistrationRow(role = "STALLION")
        val id = seedReportRow(registrationId = registrationId)

        val view = queries.findById(worldId, CoveringReportId(id))

        assert(view != null)
        assert(view!!.id == id)
        assert(view.stallionBreedingRegistrationId == registrationId)
        assert(view.coveringYear == 2024)
        assert(view.submittedOn == LocalDate.of(2024, 9, 30))
        // 種付年 2024 の期限は当年 9/30。当日提出は期限内。
        assert(!view.submittedLate)
    }

    @Test
    fun `期限を過ぎた提出は期限超過として導出される`() {
        val id = seedReportRow(submittedOn = LocalDate.of(2024, 10, 1))

        val view = queries.findById(worldId, CoveringReportId(id))

        assert(view!!.submittedLate)
    }

    @Test
    fun `存在しない ID なら null を返す`() {
        assert(queries.findById(worldId, CoveringReportId(generateId())) == null)
    }

    @Test
    fun `他の世界の種付成績報告は引けない`() {
        val otherWorldId = WorldId(createWorld())
        val otherSeeder = StudbookSeeder(otherWorldId, inspectionRows, horseRows, registrationRows)
        val id =
            seedReportRow(
                seedWorldId = otherWorldId,
                registrationId = otherSeeder.seedRegistrationRow(role = "STALLION"),
            )

        assert(queries.findById(worldId, CoveringReportId(id)) == null)
    }
}
