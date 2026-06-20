package com.example.api.domain.horseracing.model.horse.bloodhorse

import org.jmolecules.ddd.annotation.ValueObject

/**
 * 生産産駒の血統登録に際して申請者が持ち込む、仔馬自身の個体識別情報の束。
 *
 * [StudBookEntry] のうち、生産（分娩）から導出できる **生年月日（[DateOfBirth]）を除いた** 属性をまとめる。
 * 生産産駒の血統登録（registerFoal）では、出生日は繁殖成績の分娩結果（`FoalingOutcome.LiveFoal.foalingDate`）
 * から確定するため申請者入力に含めず、本束と分娩日を合わせて [StudBookEntry] を組み立てる。父・母（[BloodHorse]）は
 * 集約をまたぐ参照のため本束には含めず、ドメインサービスに別途渡す。
 *
 * @property sex 性
 * @property coatColor 毛色
 * @property breedType 品種
 * @property breeder 生産者
 * @property microchipNumber マイクロチップ番号
 * @property dnaParentage 申告された父母との DNA 型による親子判定結果
 */
@ValueObject
data class FoalIdentity(
    val sex: Sex,
    val coatColor: CoatColor,
    val breedType: BreedType,
    val breeder: Breeder,
    val microchipNumber: MicrochipNumber,
    val dnaParentage: DnaParentageResult,
) {
    /** [foalingDate] を生年月日として補い、血統登録の入力となる [StudBookEntry] を組み立てる。 */
    fun toStudBookEntry(foalingDate: DateOfBirth): StudBookEntry =
        StudBookEntry(
            sex = sex,
            coatColor = coatColor,
            breedType = breedType,
            dateOfBirth = foalingDate,
            breeder = breeder,
            microchipNumber = microchipNumber,
            dnaParentage = dnaParentage,
        )
}
