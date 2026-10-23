package com.example.api.domain.studbook.model.horse.bloodhorse

import com.example.api.domain.shared.createNonBlank
import com.github.michaelbull.result.Result
import org.jmolecules.ddd.annotation.ValueObject

/** 外国種牡馬の馬名がブランク。 */
data object BlankForeignSireName

/**
 * 外国種牡馬の馬名。
 *
 * 外国の血統登録機関に登録された名（例: Danehill）をそのまま持つ。JAIRS の馬名登録の規則を持つ [HorseName] は
 * 外国名を弾くため流用しない。ブランクでないことのみを不変条件とする。
 *
 * @property value 外国種牡馬の馬名
 */
@ValueObject
@JvmInline
value class ForeignSireName private constructor(val value: String) {
    companion object {
        /** ブランクでないことを検証して [ForeignSireName] を生成する。 */
        fun create(value: String): Result<ForeignSireName, BlankForeignSireName> =
            createNonBlank(value, BlankForeignSireName, ::ForeignSireName)
    }
}

/**
 * 外国種牡馬。日本の繁殖登録を持たず、外国の血統登録機関に登録された種牡馬。
 *
 * 持込馬（種付が外国で行われ、輸出国出国以降に生まれた馬。登録規程 第6条第1号）の父となる。持込馬の血統登録は 父の JAIRS 繁殖登録を要件とせず（同
 * 第6条第3号）、種付国の血統登録機関が発行した種付事実の証明で足りる （同 第8条第1項第4号）。そのため父を [BloodHorseId]
 * で参照せず、登録機関の国と、産駒の検証に要る属性を値として持つ。 品種は承認海外機関の血統書への登録で満たされ（登録規程実施基準 別表1(1)）、品種整合の検証に用いる。
 * 毛色は持込馬にも適用される毛色の親子整合（登録規程 第15条第6号）の検証に用いる。
 *
 * 性は持たない。種牡馬であること（雄であること）を型が表す。同じ外国種牡馬を複数の種付で名寄せする同一性は 現時点では扱わず、種付・出自ごとに値として埋め込む。
 *
 * @property name 馬名
 * @property studBookCountry 登録している血統登録機関の国
 * @property breedType 品種
 * @property coatColor 毛色
 */
@ValueObject
data class ForeignSire(
    val name: ForeignSireName,
    val studBookCountry: OriginCountry,
    val breedType: BreedType,
    val coatColor: CoatColor,
)
