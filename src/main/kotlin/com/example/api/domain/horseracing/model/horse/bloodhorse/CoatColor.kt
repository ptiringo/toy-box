package com.example.api.domain.horseracing.model.horse.bloodhorse

import org.jmolecules.ddd.annotation.ValueObject

/**
 * 毛色。
 *
 * 血統登録で個体識別の一要素として記録される。JRA / 日本軽種馬登録協会が公式に用いる 8 種を列挙する。
 * 中間的な毛色（佐目毛・月毛・河原毛など）は本モデルの対象外とし、必要になった時点で追加する。
 */
@ValueObject
enum class CoatColor {
    /** 栗毛 */
    CHESTNUT,

    /** 栃栗毛 */
    DARK_CHESTNUT,

    /** 鹿毛 */
    BAY,

    /** 黒鹿毛 */
    DARK_BAY,

    /** 青鹿毛 */
    BROWN,

    /** 青毛 */
    BLACK,

    /** 芦毛 */
    GRAY,

    /** 白毛 */
    WHITE,
}
