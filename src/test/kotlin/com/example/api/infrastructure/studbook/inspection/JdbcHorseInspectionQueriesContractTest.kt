package com.example.api.infrastructure.studbook.inspection

import com.example.api.domain.shared.WorldId
import com.example.api.domain.shared.generateId
import com.example.api.domain.studbook.model.inspection.DnaParentageResult
import com.example.api.domain.studbook.model.inspection.HorseInspectionId
import com.example.api.domain.studbook.model.inspection.IdentificationFeatures
import com.example.api.domain.studbook.model.inspection.ParentageDetermination
import com.example.api.support.PostgresContainerSupport
import java.util.UUID
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.context.TestConstructor
import org.springframework.test.context.TestConstructor.AutowireMode

/**
 * 読み取りポート HorseInspectionQueries の実装 [JdbcHorseInspectionQueries] の契約テスト （軽量 CQRS（L2）の Query
 * 側。ADR-0031）。
 *
 * 書き込みの [JdbcHorseInspectionRepositoryContractTest] と同じく本番ターゲットの PostgreSQL（Testcontainers、
 * [PostgresContainerSupport] で共有）に対して検証する。コンテキスト構成も write 側と同一（`@SpringBootTest(NONE)`）
 * のためコンテキストキャッシュを共有する（testing.md）。
 *
 * 検証する契約:
 * 1. `horse_inspection` テーブルへ直接 SELECT し、集約を組まずに View へ詰められること
 * 2. 判別子（parentage_type / dna_parentage_result）から sealed [ParentageDetermination] を復元できること（全 4 区分）
 * 3. feature_* 列が全 NULL なら features が null に、値があれば [IdentificationFeatures] に復元されること
 * 4. 該当行が無ければ null を返すこと
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestConstructor(autowireMode = AutowireMode.ALL)
class JdbcHorseInspectionQueriesContractTest(
    private val jdbcClient: JdbcClient,
    private val rows: HorseInspectionSpringDataRepository,
) : PostgresContainerSupport() {
    // WorldId は value class で lateinit を付けられないため、生 UUID を保持して都度包む
    private lateinit var worldIdValue: UUID
    private val worldId
        get() = WorldId(worldIdValue)

    /** 基底クラスの TRUNCATE（@BeforeEach）の後に世界を作る。 */
    @BeforeEach
    fun setUpWorld() {
        worldIdValue = createWorld()
    }

    private val queries = JdbcHorseInspectionQueries(jdbcClient)

    @Test
    fun `DNA判定と特徴記述子を持つ審査をIDでViewとして引ける`() {
        val id = generateId()
        rows.save(
            HorseInspectionRow(
                worldId = worldId.value,
                id = id,
                microchipNumber = "392140000000001",
                parentageType = "BY_DNA",
                dnaParentageResult = "CONSISTENT",
                featureHairWhorl = "頭部正中",
                featureWhiteMarkings = "左後一白",
                featureNosePrint = "渦紋",
            )
        )

        val view = queries.findById(worldId, HorseInspectionId(id))

        assert(view != null)
        assert(view!!.id == id)
        assert(view.microchipNumber == "392140000000001")
        assert(view.parentage == ParentageDetermination.ByDna(DnaParentageResult.CONSISTENT))
        assert(view.features == IdentificationFeatures("頭部正中", "左後一白", "渦紋"))
    }

    @Test
    fun `血液型フォールバックの審査は特徴なし（全NULL）で features が null になる`() {
        val id = generateId()
        rows.save(
            HorseInspectionRow(
                worldId = worldId.value,
                id = id,
                microchipNumber = "392140000000002",
                parentageType = "BY_BLOOD_TYPE",
            )
        )

        val view = queries.findById(worldId, HorseInspectionId(id))

        assert(view!!.parentage == ParentageDetermination.ByBloodType)
        assert(view.features == null)
    }

    @Test
    fun `海外機関判定の審査を復元できる`() {
        val id = generateId()
        rows.save(
            HorseInspectionRow(
                worldId = worldId.value,
                id = id,
                microchipNumber = "392140000000003",
                parentageType = "BY_OVERSEAS_INSTITUTION",
            )
        )

        assert(
            queries.findById(worldId, HorseInspectionId(id))!!.parentage ==
                ParentageDetermination.ByOverseasInstitution
        )
    }

    @Test
    fun `判定対象外の審査を復元できる`() {
        val id = generateId()
        rows.save(
            HorseInspectionRow(
                worldId = worldId.value,
                id = id,
                microchipNumber = "392140000000004",
                parentageType = "NOT_APPLICABLE",
            )
        )

        assert(
            queries.findById(worldId, HorseInspectionId(id))!!.parentage ==
                ParentageDetermination.NotApplicable
        )
    }

    @Test
    fun `存在しないIDのfindByIdはnullを返す`() {
        assert(queries.findById(worldId, HorseInspectionId(generateId())) == null)
    }
}
