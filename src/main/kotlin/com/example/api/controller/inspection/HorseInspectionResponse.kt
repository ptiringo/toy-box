package com.example.api.controller.inspection

import com.example.api.application.studbook.inspection.HorseInspectionView
import com.example.api.domain.studbook.model.inspection.HorseInspection
import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

/**
 * 審査リソースの表現（HTTP 契約）。
 *
 * 審査リソースに対する操作（Create / Get）は、AIP-133 / AIP-131 に倣い一律でこのリソース表現全体を返す （ADR-0008）。相互排他な親子判定は入れ子
 * `parentage` の oneOf にする（ADR-0020）。
 *
 * @property id 審査の生 UUID
 * @property microchipNumber マイクロチップ番号
 * @property parentage 親子判定
 * @property features 特徴記述子。未記録なら null
 */
@Schema(description = "審査リソースの表現")
data class HorseInspectionResponse(
    val id: UUID,
    val microchipNumber: String,
    val parentage: ParentageDeterminationDto,
    val features: IdentificationFeaturesDto?,
)

/** [HorseInspection] を審査リソースの表現へ変換する（書き込み経路。Create の成功レスポンス）。 */
fun HorseInspection.toResponse(): HorseInspectionResponse =
    HorseInspectionResponse(
        id = id.value,
        microchipNumber = microchipNumber.value,
        parentage = parentage.toApi(),
        features = features?.toApi(),
    )

/**
 * 読み取りビュー [HorseInspectionView] を審査リソースの表現へ変換する。
 *
 * 軽量 CQRS（L2）の読み取り経路（Get）も、書き込み経路（Create）と**同一の単一リソース表現**を返す（ADR-0008）。
 */
fun HorseInspectionView.toResponse(): HorseInspectionResponse =
    HorseInspectionResponse(
        id = id,
        microchipNumber = microchipNumber,
        parentage = parentage.toApi(),
        features = features?.toApi(),
    )
