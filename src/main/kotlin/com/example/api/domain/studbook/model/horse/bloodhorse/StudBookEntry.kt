package com.example.api.domain.studbook.model.horse.bloodhorse

import org.jmolecules.ddd.annotation.ValueObject

/**
 * DNA 型による親子判定の結果。
 *
 * 血統登録では、申告された父・母との親子関係を DNA 型検査で確認する。矛盾がない（[CONSISTENT]）場合のみ血統登録できる。
 */
@ValueObject
enum class DnaParentageResult {
    /** 申告どおりの親子関係と矛盾しない。 */
    CONSISTENT,

    /** 申告された親子関係と矛盾する。 */
    INCONSISTENT,

    /** 未検査。 */
    UNTESTED,
}

/**
 * 血統登録申請に際して申請者が持ち込む、仔馬自身の個体識別情報の束。
 *
 * 父・母（[BloodHorse]）は集約をまたぐ参照のため本束には含めず、ドメインサービス registerInStudBook に別途渡す。 本束は仔馬自身の属性と、申告された親子関係の
 * DNA 判定結果のみを保持する。
 *
 * @property sex 性
 * @property coatColor 毛色
 * @property breedType 品種
 * @property dateOfBirth 生年月日
 * @property breeder 生産者
 * @property microchipNumber マイクロチップ番号
 * @property dnaParentage 申告された父母との DNA 型による親子判定結果
 */
@ValueObject
data class StudBookEntry(
    val sex: Sex,
    val coatColor: CoatColor,
    val breedType: BreedType,
    val dateOfBirth: DateOfBirth,
    val breeder: Breeder,
    val microchipNumber: MicrochipNumber,
    val dnaParentage: DnaParentageResult,
)
