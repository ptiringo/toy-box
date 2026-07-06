package com.example.api.controller.inspection

import com.example.api.domain.studbook.model.inspection.IdentificationFeatures
import io.swagger.v3.oas.annotations.media.Schema

/**
 * 審査リソースの「特徴記述子」の表現（HTTP 契約）。
 *
 * 旋毛・白斑・鼻紋を保持し、いずれも任意（未記録なら null）。ドメインの [IdentificationFeatures] と同形だが wire 契約として独立させる（ADR-0007
 * と同趣旨）。
 *
 * @property hairWhorl 旋毛の記述
 * @property whiteMarkings 白斑（四肢別）の記述
 * @property nosePrint 鼻紋の記述
 */
@Schema(description = "審査リソースの特徴記述子（旋毛・白斑・鼻紋。いずれも任意）")
data class IdentificationFeaturesDto(
    val hairWhorl: String?,
    val whiteMarkings: String?,
    val nosePrint: String?,
)

/**
 * HTTP 契約の特徴記述子をドメインへ変換する。
 *
 * **全フィールド null は不在（null）へ正規化する**。永続化が「feature_* 列すべて NULL＝未記録」で在不在を 表すため（ADR-0043
 * の共在フラット化）、ここで正規化しないと Create 直後の応答（features あり）と Get の応答（features null）が食い違う。
 */
fun IdentificationFeaturesDto.toDomain(): IdentificationFeatures? =
    if (hairWhorl == null && whiteMarkings == null && nosePrint == null) {
        null
    } else {
        IdentificationFeatures(
            hairWhorl = hairWhorl,
            whiteMarkings = whiteMarkings,
            nosePrint = nosePrint,
        )
    }

/** ドメインの特徴記述子を HTTP 契約へ変換する。 */
fun IdentificationFeatures.toApi(): IdentificationFeaturesDto =
    IdentificationFeaturesDto(
        hairWhorl = hairWhorl,
        whiteMarkings = whiteMarkings,
        nosePrint = nosePrint,
    )
