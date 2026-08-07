package com.example.api.infrastructure.studbook.inspection

import com.example.api.domain.shared.WorldId
import com.example.api.domain.shared.generateId
import com.example.api.domain.studbook.model.inspection.DnaParentageResult
import com.example.api.domain.studbook.model.inspection.HorseInspection
import com.example.api.domain.studbook.model.inspection.HorseInspectionId
import com.example.api.domain.studbook.model.inspection.IdentificationFeatures
import com.example.api.domain.studbook.model.inspection.MicrochipNumber
import com.example.api.domain.studbook.model.inspection.ParentageDetermination
import com.example.api.support.PostgresContainerSupport
import com.github.michaelbull.result.unwrap
import java.util.UUID
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.test.context.TestConstructor
import org.springframework.test.context.TestConstructor.AutowireMode

/**
 * ドメインポート HorseInspectionRepository の Spring Data JDBC 実装 [JdbcHorseInspectionRepository] の契約テスト。
 *
 * 検証する契約:
 * 1. value class ID・VO（MicrochipNumber）を永続化モデル分離＋手書きマッパーで橋渡しできること
 * 2. 外部採番（UUIDv7）で @Id が常に非 null でも @Version が null のとき insert と判定されること
 * 3. sealed な親子判定（ByDna／ByBloodType／NotApplicable）が判別子フラット化を経て往復できること
 * 4. 特徴記述子（IdentificationFeatures）の記録あり／なし（全 NULL）が往復できること
 * 5. 親子判定の不変条件（BY_DNA のときだけ DNA 結果を持つ）が CHECK 制約で強制されること
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestConstructor(autowireMode = AutowireMode.ALL)
class JdbcHorseInspectionRepositoryContractTest(
    private val rows: HorseInspectionSpringDataRepository
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

    private val repository = JdbcHorseInspectionRepository(rows)
    private val microchip = MicrochipNumber.create("392140000000001").unwrap()

    private fun byDnaRow(id: UUID = generateId()) =
        HorseInspectionRow(
            worldId = worldId.value,
            id = id,
            microchipNumber = "392140000000001",
            parentageType = "BY_DNA",
            dnaParentageResult = "CONSISTENT",
        )

    @Test
    fun `外部採番のIDを持つ新規行はversionがnullなのでinsertされる`() {
        val id = generateId()
        val saved = rows.save(byDnaRow(id = id))

        assert(saved.id == id)
        assert(saved.version != null)
        assert(rows.count() == 1L)
    }

    @Test
    fun `DNA 親子判定の審査は判別子と DNA 結果ごと往復できる`() {
        val inspection =
            HorseInspection.create(
                microchip,
                ParentageDetermination.ByDna(DnaParentageResult.CONSISTENT),
            )

        val saved = repository.save(worldId, inspection)
        val found = repository.findById(worldId, inspection.id)

        assert(saved.id == inspection.id)
        assert(found != null)
        assert(found!!.id == inspection.id)
        assert(found.microchipNumber == microchip)
        assert(found.parentage == ParentageDetermination.ByDna(DnaParentageResult.CONSISTENT))
        assert(found.features == null)
    }

    @Test
    fun `フォールバック区分と特徴記述子ごと往復できる`() {
        val features =
            IdentificationFeatures(hairWhorl = "額上部", whiteMarkings = "左前白", nosePrint = null)
        val inspection =
            HorseInspection.create(microchip, ParentageDetermination.NotApplicable, features)

        repository.save(worldId, inspection)
        val found = repository.findById(worldId, inspection.id)

        assert(found != null)
        assert(found!!.parentage == ParentageDetermination.NotApplicable)
        assert(found.features == features)
    }

    @Test
    fun `血液型検査区分の審査は判別子ごと往復できる`() {
        val inspection = HorseInspection.create(microchip, ParentageDetermination.ByBloodType)

        repository.save(worldId, inspection)
        val found = repository.findById(worldId, inspection.id)

        assert(found != null)
        assert(found!!.id == inspection.id)
        assert(found.parentage == ParentageDetermination.ByBloodType)
        assert(found.features == null)
    }

    @Test
    fun `承認海外機関区分の審査は判別子ごと往復できる`() {
        val inspection =
            HorseInspection.create(microchip, ParentageDetermination.ByOverseasInstitution)

        repository.save(worldId, inspection)
        val found = repository.findById(worldId, inspection.id)

        assert(found != null)
        assert(found!!.id == inspection.id)
        assert(found.parentage == ParentageDetermination.ByOverseasInstitution)
        assert(found.features == null)
    }

    @Test
    fun `存在しないIDのfindByIdはnullを返す`() {
        assert(repository.findById(worldId, HorseInspectionId(generateId())) == null)
    }

    @Test
    fun `DNA区分でないのにDNA結果を持つ行はCHECK制約で拒否される`() {
        // NOT_APPLICABLE なのに dna_parentage_result を持つ＝ ParentageDetermination の不変条件違反。
        val inconsistent =
            byDnaRow().copy(parentageType = "NOT_APPLICABLE", dnaParentageResult = "CONSISTENT")

        assertThrows<DataIntegrityViolationException> { rows.save(inconsistent) }
    }
}
