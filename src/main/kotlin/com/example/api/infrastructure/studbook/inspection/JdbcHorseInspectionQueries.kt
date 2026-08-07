package com.example.api.infrastructure.studbook.inspection

import com.example.api.application.studbook.inspection.HorseInspectionQueries
import com.example.api.application.studbook.inspection.HorseInspectionView
import com.example.api.domain.shared.WorldId
import com.example.api.domain.studbook.model.inspection.HorseInspectionId
import java.util.UUID
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

/**
 * 読み取りポート [HorseInspectionQueries] の実装（軽量 CQRS（L2）の Query 側。ADR-0031）。
 *
 * 書き込みの [JdbcHorseInspectionRepository]（集約を [HorseInspectionRow] 経由で復元する）とは**別経路**として、
 * `horse_inspection` テーブルを [JdbcClient] で直接 SELECT し、集約を一切組まずに [HorseInspectionView] へ詰める。
 * 判別子・特徴記述子の列変換は write 経路と共有する（`HorseInspectionColumnMapping.kt`）。
 *
 * 楽観ロックの `version` 列は読み取りでは不要なため SELECT しない（read は整合性境界を持たない）。
 */
@Repository
class JdbcHorseInspectionQueries(private val jdbcClient: JdbcClient) : HorseInspectionQueries {

    override fun findById(worldId: WorldId, id: HorseInspectionId): HorseInspectionView? =
        jdbcClient
            .sql(
                """
                SELECT id, microchip_number, parentage_type, dna_parentage_result,
                    feature_hair_whorl, feature_white_markings, feature_nose_print
                FROM studbook.horse_inspection
                WHERE id = :id AND world_id = :worldId
                """
                    .trimIndent()
            )
            .param("id", id.value)
            .param("worldId", worldId.value)
            .query { rs, _ ->
                HorseInspectionView(
                    id = rs.getObject("id", UUID::class.java),
                    microchipNumber = rs.getString("microchip_number"),
                    parentage =
                        toParentageDetermination(
                            parentageType = rs.getString("parentage_type"),
                            dnaParentageResult = rs.getString("dna_parentage_result"),
                        ),
                    features =
                        toIdentificationFeatures(
                            hairWhorl = rs.getString("feature_hair_whorl"),
                            whiteMarkings = rs.getString("feature_white_markings"),
                            nosePrint = rs.getString("feature_nose_print"),
                        ),
                )
            }
            .optional()
            .orElse(null)
}
