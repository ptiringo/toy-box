package com.example.api.domain.studbook.model.horse.bloodhorse

import org.jmolecules.ddd.annotation.ValueObject

/**
 * 移行取り込み（carried-over）で取り込む馬の個体識別情報の束。
 *
 * 先行する登録原簿に血統登録済みの馬をシステム境界で取り込む際の入力であり、JAIRS への新規登録申請では ない（#633 / ADR-0069）。父母・血統は先行原簿に記録済みのため父母 ID
 * を持たず、輸入馬 （[ImportedHorseEntry]）と異なり原産国・揚陸日も持たない。
 *
 * @property sex 性
 * @property coatColor 毛色
 * @property breedType 品種（先行原簿の記録に基づく）
 * @property dateOfBirth 生年月日（先行原簿の記録に基づく）
 * @property breeder 生産者
 */
@ValueObject
data class CarriedOverHorseEntry(
    val sex: Sex,
    val coatColor: CoatColor,
    val breedType: BreedType,
    val dateOfBirth: DateOfBirth,
    val breeder: Breeder,
)
