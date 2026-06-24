package com.example.api.domain.studbook.model.horse.bloodhorse

import org.jmolecules.ddd.annotation.ValueObject

/**
 * 輸入馬・基礎輸入馬の血統登録申請に際して申請者が持ち込む、馬自身の個体識別情報の束。
 *
 * 通常の内国産馬（[StudBookEntry]）と異なり、**父・母が当システムに存在しない**ことを前提とする。このため父母 ID や 申告された親子関係の DNA
 * 判定結果（[DnaParentageResult]）は含めない。品種は承認海外機関の血統書・輸出証明書に依拠するため、
 * 父母の品種との整合検証も行わない（これらの輸入特有の審査は別途のモデリングに委ねる）。
 *
 * 代わりに輸入馬固有の属性として、原産国（[originCountry]）と揚陸日（[landingDate]）を保持する。
 *
 * @property sex 性
 * @property coatColor 毛色
 * @property breedType 品種（承認海外機関の血統書・輸出証明書に基づく）
 * @property dateOfBirth 生年月日（海外血統書に基づく）
 * @property breeder 生産者
 * @property microchipNumber マイクロチップ番号
 * @property originCountry 原産国
 * @property landingDate 揚陸日（陸揚げされた日。登録の起算点）
 */
@ValueObject
data class ImportedHorseEntry(
    val sex: Sex,
    val coatColor: CoatColor,
    val breedType: BreedType,
    val dateOfBirth: DateOfBirth,
    val breeder: Breeder,
    val microchipNumber: MicrochipNumber,
    val originCountry: OriginCountry,
    val landingDate: LandingDate,
)
