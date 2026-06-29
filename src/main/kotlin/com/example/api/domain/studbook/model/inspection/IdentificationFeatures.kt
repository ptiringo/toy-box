package com.example.api.domain.studbook.model.inspection

import org.jmolecules.ddd.annotation.ValueObject

/**
 * 個体識別審査で記録する馬の特徴記述子（様式4号裏・1号裏）。
 *
 * 旋毛（[hairWhorl]）・白斑（四肢別の記述。[whiteMarkings]）・鼻紋（[nosePrint]）を保持する。本スライスでは 軽くモデル化し、いずれも任意（未記録なら
 * null）。詳細な様式（馬体図・個体確認書, 様式8号）は対象外。
 *
 * @property hairWhorl 旋毛の記述
 * @property whiteMarkings 白斑（四肢別）の記述
 * @property nosePrint 鼻紋の記述
 */
@ValueObject
data class IdentificationFeatures(
    val hairWhorl: String?,
    val whiteMarkings: String?,
    val nosePrint: String?,
)
