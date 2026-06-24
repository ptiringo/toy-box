package com.example.api.controller.horse

import com.example.api.application.studbook.horse.RegisterImportedHorseCommand
import java.time.LocalDate

/**
 * `POST /api/blood_horses:registerImported` のリクエストボディ。
 *
 * 輸入馬・基礎輸入馬の登録申請フォームに相当する。内国産馬（[RegisterBloodHorseRequest]）と異なり父母 ID・DNA 判定は受け取らず、
 * 代わりに原産国・揚陸日を受け取る。enum 項目（性・毛色・品種）は HTTP 契約専用の `〜Dto` enum で受け取り、ドメイン enum への変換は [toCommand] が
 * [toDomain] マッピングで行う。VO で表す項目（番号・マイクロチップ・生産者・原産国）は素の文字列で受け取り、 ユースケースで検証する。
 *
 * @property sex 性
 * @property coatColor 毛色
 * @property breedType 品種
 * @property dateOfBirth 生年月日
 * @property breeder 生産者名
 * @property microchipNumber マイクロチップ番号
 * @property originCountry 原産国名
 * @property landingDate 揚陸日
 * @property registrationNumber 交付される血統登録番号
 */
@Suppress("LongParameterList") // 登録申請フォーム相当の境界入力であり、項目の分割はかえって意味を損なう
data class RegisterImportedHorseRequest(
    val sex: SexDto,
    val coatColor: CoatColorDto,
    val breedType: BreedTypeDto,
    val dateOfBirth: LocalDate,
    val breeder: String,
    val microchipNumber: String,
    val originCountry: String,
    val landingDate: LocalDate,
    val registrationNumber: String,
)

/** リクエストボディを輸入馬血統登録ユースケースの入力コマンドへ変換する。境界 DTO ↔ コマンドのフィールド対応はここに集約する。 */
fun RegisterImportedHorseRequest.toCommand(): RegisterImportedHorseCommand =
    RegisterImportedHorseCommand(
        sex = sex.toDomain(),
        coatColor = coatColor.toDomain(),
        breedType = breedType.toDomain(),
        dateOfBirth = dateOfBirth,
        breeder = breeder,
        microchipNumber = microchipNumber,
        originCountry = originCountry,
        landingDate = landingDate,
        registrationNumber = registrationNumber,
    )
