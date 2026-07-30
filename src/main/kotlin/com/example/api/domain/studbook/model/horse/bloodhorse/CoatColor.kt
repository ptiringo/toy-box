package com.example.api.domain.studbook.model.horse.bloodhorse

import org.jmolecules.ddd.annotation.ValueObject

/**
 * 毛色。
 *
 * 血統登録で個体識別の一要素として記録される。JRA / 日本軽種馬登録協会が公式に用いる 8 種を列挙する。
 * 中間的な毛色（佐目毛・月毛・河原毛など）は本モデルの対象外とし、必要になった時点で追加する。 父母の毛色との整合（芦毛・栗毛の遺伝ルール）は [violatesGrayRule] /
 * [violatesChestnutRule] で判定する （登録規程実施基準 第9条第1項(2)。#317）。
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
    WHITE;

    /** 栗毛族（栗毛・栃栗毛）か。第9条第1項(2)イの「栗毛（栃栗毛を含む。以下同じ。）」に対応する。 */
    private val isChestnut: Boolean
        get() = this == CHESTNUT || this == DARK_CHESTNUT

    /** 芦毛の遺伝子を持ちうる毛色か。芦毛に加え、第2条第3項が芦毛の遺伝子検査の対象に含める白毛を含む。 */
    private val mayCarryGrayGene: Boolean
        get() = this == GRAY || this == WHITE

    /**
     * この毛色を仔の毛色としたとき、第9条第1項(2)ア（芦毛以外の父母の間に生まれた馬にあっては、芦毛のもの）に 該当するか判定する。`true` なら登録要件違反。
     *
     * 父母のいずれかが芦毛または白毛なら該当しない。条文の文言は「芦毛以外の父母」だが、第2条第3項が芦毛の
     * 遺伝子検査の対象に白毛の父母を含めており、白毛の親から芦毛の仔が生じうることを制度が想定しているため （遺伝学的にも白毛の W 遺伝子と芦毛の G
     * 遺伝子は独立）、白毛の父母も対象外とする。判定できないケースを 違反として弾かない方針は [BreedType.isConsistentWith] と一貫する。
     */
    fun violatesGrayRule(sire: CoatColor, dam: CoatColor): Boolean =
        this == GRAY && !sire.mayCarryGrayGene && !dam.mayCarryGrayGene

    /**
     * この毛色を仔の毛色としたとき、第9条第1項(2)イ（栗毛の父母の間に生まれた馬にあっては、栗毛以外のもの）に 該当するか判定する。`true` なら登録要件違反。
     *
     * 仔が白毛の場合は該当しない。第9条第1項(2)の柱書きが「白毛以外の7種の毛色に関し」と対象を限定しているため。
     */
    fun violatesChestnutRule(sire: CoatColor, dam: CoatColor): Boolean =
        this != WHITE && sire.isChestnut && dam.isChestnut && !this.isChestnut
}
