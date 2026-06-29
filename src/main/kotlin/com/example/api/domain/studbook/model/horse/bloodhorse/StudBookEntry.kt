package com.example.api.domain.studbook.model.horse.bloodhorse

import org.jmolecules.ddd.annotation.ValueObject

/**
 * 血統登録申請に際して申請者が持ち込む、仔馬自身の個体識別情報の束。
 *
 * 父・母（[BloodHorse]）は集約をまたぐ参照のため本束には含めず、ドメインサービス registerInStudBook に別途渡す。
 * 識別子（マイクロチップ）・親子判定は審査（[com.example.api.domain.studbook.model.inspection.HorseInspection]）が
 * 保持するため本束には含めず、仔馬自身の属性のみを保持する。
 *
 * @property sex 性
 * @property coatColor 毛色
 * @property breedType 品種
 * @property dateOfBirth 生年月日
 * @property breeder 生産者
 */
@ValueObject
data class StudBookEntry(
    val sex: Sex,
    val coatColor: CoatColor,
    val breedType: BreedType,
    val dateOfBirth: DateOfBirth,
    val breeder: Breeder,
)
