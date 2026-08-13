package com.example.api.infrastructure.studbook.breeding

import com.example.api.domain.shared.WorldId
import com.example.api.domain.shared.generateId
import com.example.api.domain.studbook.model.breeding.BreedingRegistrationId
import com.example.api.domain.studbook.model.breeding.BreedingRole
import com.example.api.domain.studbook.model.breeding.RetirementReason
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
 * 読み取りポート [com.example.api.application.studbook.breeding.BreedingRegistrationQueries] の JDBC 実装
 * [JdbcBreedingRegistrationQueries] の契約テスト（軽量 CQRS / L2。ADR-0031）。
 *
 * 本番ターゲットの PostgreSQL（Testcontainers、[PostgresContainerSupport] で共有）に対し、列マッピングと 世界スコープ（`world_id`
 * の絞り込み）が正しいかを検証する。行の投入は [StudbookSeeder] の row 系で行う。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestConstructor(autowireMode = AutowireMode.ALL)
class JdbcBreedingRegistrationQueriesContractTest(
    private val jdbcClient: JdbcClient,
    private val horseRows: BloodHorseSpringDataRepository,
    private val inspectionRows: HorseInspectionSpringDataRepository,
    private val registrationRows: BreedingRegistrationSpringDataRepository,
) : PostgresContainerSupport() {

    private val queries = JdbcBreedingRegistrationQueries(jdbcClient)
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
    fun `供用中の繁殖登録を ID で引き列を詰めて返す`() {
        val id = seeder.seedRegistrationRow(role = "BROODMARE")

        val view = queries.findById(worldId, BreedingRegistrationId(id))

        assert(view != null)
        assert(view!!.id == id)
        assert(view.registrationNumber == "B-0001")
        assert(view.role == BreedingRole.BROODMARE)
        // 供用中は事由・発生日の 2 列とも NULL。共在 VO の在不在をそのまま写せていること。
        assert(view.retirement == null)
    }

    @Test
    fun `供用停止済みの繁殖登録は事由と発生日を持つ`() {
        val id = generateId()
        seedRetiredRegistrationRow(id)

        val view = queries.findById(worldId, BreedingRegistrationId(id))

        assert(view != null)
        val retirement = view!!.retirement
        assert(retirement != null)
        assert(retirement!!.reason == RetirementReason.DEATH)
        assert(retirement.occurredOn == LocalDate.of(2025, 3, 1))
    }

    @Test
    fun `存在しない ID なら null を返す`() {
        assert(queries.findById(worldId, BreedingRegistrationId(generateId())) == null)
    }

    @Test
    fun `他の世界の繁殖登録は引けない`() {
        val otherWorldId = WorldId(createWorld())
        val otherSeeder = StudbookSeeder(otherWorldId, inspectionRows, horseRows, registrationRows)
        val id = otherSeeder.seedRegistrationRow()

        assert(queries.findById(worldId, BreedingRegistrationId(id)) == null)
    }

    /** 供用停止済み（事由・発生日が非 NULL）の繁殖登録行を対象馬ごと作る。 */
    private fun seedRetiredRegistrationRow(id: UUID) {
        registrationRows.save(
            BreedingRegistrationRow(
                worldId = worldId.value,
                id = id,
                registrationNumber = "B-0002",
                registeredHorseId = seeder.seedHorseRow(sex = "MALE"),
                breedingRole = "STALLION",
                retirementReason = "DEATH",
                retirementOccurredOn = LocalDate.of(2025, 3, 1),
            )
        )
    }
}
