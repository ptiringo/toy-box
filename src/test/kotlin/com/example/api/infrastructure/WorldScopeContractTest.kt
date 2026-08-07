package com.example.api.infrastructure

import com.example.api.domain.shared.generateId
import com.example.api.infrastructure.studbook.horse.BloodHorseRow
import com.example.api.infrastructure.studbook.horse.BloodHorseSpringDataRepository
import com.example.api.infrastructure.studbook.inspection.HorseInspectionRow
import com.example.api.infrastructure.studbook.inspection.HorseInspectionSpringDataRepository
import com.example.api.support.PostgresContainerSupport
import java.time.LocalDate
import java.util.UUID
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate

/**
 * 世界スコープの DB レベル backstop に対する契約テスト（#704 / ADR-0067）。
 *
 * 「世界をまたいだ参照が成立しない」ことは複合 FK だけが担保しており、Kotlin 側のコードに対応物が無い （アプリは自分の世界しか読まないので、そもそも他人の世界の ID
 * を掴めない）。したがってここで直接 行を書き込んで確かめる（`IamSchemaContractTest` と同型）。
 *
 * 生 SQL ではなく Row 型と Spring Data リポジトリを使うのは、列名・NOT NULL・CHECK 制約（`chk_blood_horse_origin`
 * 等）の追随をマッピングに任せ、検証したい制約だけを浮かび上がらせるため。
 */
@org.springframework.boot.test.context.SpringBootTest(
    webEnvironment = org.springframework.boot.test.context.SpringBootTest.WebEnvironment.NONE
)
class WorldScopeContractTest : PostgresContainerSupport() {

    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var inspectionRows: HorseInspectionSpringDataRepository
    @Autowired private lateinit var horseRows: BloodHorseSpringDataRepository

    /** マイクロチップ番号は 15 桁。テスト内で連番を振って一意にする。 */
    private var microchipSequence = 0

    private fun insertInspection(worldId: UUID): UUID {
        microchipSequence++
        val id = generateId()
        inspectionRows.save(
            HorseInspectionRow(
                id = id,
                worldId = worldId,
                microchipNumber = "39214%09d".format(microchipSequence),
                parentageType = "NOT_APPLICABLE",
            )
        )
        return id
    }

    /** 軽種馬を 1 頭 insert する。父母を渡すと内国産、渡さなければ輸入馬として組む。 */
    private fun insertHorse(
        worldId: UUID,
        name: String? = null,
        sireId: UUID? = null,
        damId: UUID? = null,
    ): UUID {
        val id = generateId()
        val domestic = sireId != null && damId != null
        horseRows.save(
            BloodHorseRow(
                id = id,
                worldId = worldId,
                registrationNumber = "WS-$id",
                sex = "MALE",
                coatColor = "BAY",
                breedType = "THOROUGHBRED",
                dateOfBirth = LocalDate.of(2024, 4, 1),
                breeder = "検証牧場",
                inspectionId = insertInspection(worldId),
                name = name,
                originType = if (domestic) "DOMESTIC" else "IMPORTED",
                sireId = sireId,
                damId = damId,
                originCountry = if (domestic) null else "アイルランド",
                landingDate = if (domestic) null else LocalDate.of(2025, 1, 10),
            )
        )
        return id
    }

    private fun countBy(table: String, worldId: UUID): Int =
        jdbc.queryForObject(
            "SELECT count(*) FROM $table WHERE world_id = ?",
            Int::class.java,
            worldId,
        ) ?: 0

    @Test
    fun `他の世界の馬を父に指定した軽種馬は insert できない`() {
        val worldA = createWorld("世界A")
        val worldB = createWorld("世界B")
        val sireInA = insertHorse(worldA)
        val damInB = insertHorse(worldB)

        var rejected = false
        try {
            insertHorse(worldB, sireId = sireInA, damId = damInB)
        } catch (_: DataIntegrityViolationException) {
            rejected = true
        }

        assert(rejected)
    }

    @Test
    fun `同じ世界の馬を父母に指定した軽種馬は insert できる`() {
        val worldId = createWorld()
        val sireId = insertHorse(worldId)
        val damId = insertHorse(worldId)

        insertHorse(worldId, sireId = sireId, damId = damId)

        assert(countBy("studbook.blood_horse", worldId) == 3)
    }

    @Test
    fun `存在しない世界の行は insert できない`() {
        var rejected = false
        try {
            insertHorse(generateId())
        } catch (_: DataIntegrityViolationException) {
            rejected = true
        }

        assert(rejected)
    }

    @Test
    fun `同じ世界に同名の馬は登録できない`() {
        val worldId = createWorld()
        insertHorse(worldId, name = "アカイイナズマ")

        var rejected = false
        try {
            insertHorse(worldId, name = "アカイイナズマ")
        } catch (_: DataIntegrityViolationException) {
            rejected = true
        }

        assert(rejected)
    }

    @Test
    fun `別の世界であれば同名の馬を登録できる`() {
        val worldA = createWorld("世界A")
        val worldB = createWorld("世界B")

        insertHorse(worldA, name = "アカイイナズマ")
        insertHorse(worldB, name = "アカイイナズマ")

        val count =
            jdbc.queryForObject(
                "SELECT count(*) FROM studbook.blood_horse WHERE name = ?",
                Int::class.java,
                "アカイイナズマ",
            )
        assert(count == 2)
    }

    @Test
    fun `世界を削除すると配下の軽種馬と審査も連鎖削除される`() {
        // 集約間 FK を ON DELETE NO ACTION のままにした判断（V19）が、世界削除の連鎖で成立することの実測。
        // ここが落ちるなら集約間 FK も CASCADE へ倒す必要がある。
        val worldId = createWorld("消える世界")
        val sireId = insertHorse(worldId)
        val damId = insertHorse(worldId)
        insertHorse(worldId, sireId = sireId, damId = damId)

        jdbc.update("DELETE FROM iam.world WHERE id = ?", worldId)

        assert(countBy("studbook.blood_horse", worldId) == 0)
        assert(countBy("studbook.horse_inspection", worldId) == 0)
    }
}
