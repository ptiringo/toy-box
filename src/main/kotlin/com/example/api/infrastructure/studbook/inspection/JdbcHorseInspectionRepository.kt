package com.example.api.infrastructure.studbook.inspection

import com.example.api.domain.shared.WorldId
import com.example.api.domain.studbook.model.inspection.HorseInspection
import com.example.api.domain.studbook.model.inspection.HorseInspectionId
import com.example.api.domain.studbook.model.inspection.HorseInspectionRepository
import com.example.api.domain.studbook.model.inspection.IdentificationFeatures
import com.example.api.domain.studbook.model.inspection.MicrochipNumber
import com.example.api.domain.studbook.model.inspection.ParentageDetermination
import com.example.api.infrastructure.shared.orThrow
import org.springframework.stereotype.Repository

/**
 * ドメインポート [HorseInspectionRepository] の唯一の実装。Spring Data JDBC で永続化する（ADR-0027 / ADR-0030）。
 *
 * ドメイン集約 [HorseInspection] と永続化モデル [HorseInspectionRow] を手書きマッパーで相互変換し、CRUD は
 * [HorseInspectionSpringDataRepository] へ委譲する。value class ID・VO（`MicrochipNumber`）・sealed な親子判定
 * [ParentageDetermination] の判別子フラット化・nullable な [IdentificationFeatures] のフラット化を本マッパーが担う。
 */
@Repository
class JdbcHorseInspectionRepository(private val rows: HorseInspectionSpringDataRepository) :
    HorseInspectionRepository {

    override fun findById(worldId: WorldId, id: HorseInspectionId): HorseInspection? =
        rows.findByWorldIdAndId(worldId.value, id.value)?.toDomain()

    override fun save(worldId: WorldId, inspection: HorseInspection): HorseInspection =
        rows.save(inspection.toRow(worldId)).toDomain()

    /** 永続化モデルからドメイン集約を再構成する（検証・採番なし）。 */
    private fun HorseInspectionRow.toDomain(): HorseInspection =
        HorseInspection.reconstitute(
            id = HorseInspectionId(id),
            microchipNumber = MicrochipNumber.create(microchipNumber).orThrow(),
            parentage = toParentageDetermination(parentageType, dnaParentageResult),
            features =
                toIdentificationFeatures(featureHairWhorl, featureWhiteMarkings, featureNosePrint),
        )

    /**
     * ドメイン集約を永続化モデルへ写す。
     *
     * [HorseInspection] は審査という INSERT-only のイベント（ADR-0041）のため、`Entity.version`（既定の `null`）を
     * override せず version を持たない。常に insert のみを扱い update 経路は持たない（ADR-0047）。
     */
    private fun HorseInspection.toRow(worldId: WorldId): HorseInspectionRow {
        val (parentageType, dnaResult) = parentage.toTypeAndResult()
        return HorseInspectionRow(
            id = id.value,
            worldId = worldId.value,
            microchipNumber = microchipNumber.value,
            parentageType = parentageType,
            dnaParentageResult = dnaResult,
            featureHairWhorl = features?.hairWhorl,
            featureWhiteMarkings = features?.whiteMarkings,
            featureNosePrint = features?.nosePrint,
        )
    }
}
