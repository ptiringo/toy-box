package com.example.api.controller.horse.request

import com.example.api.application.studbook.horse.RegisterInStudBookCommand
import com.example.api.controller.horse.BreedTypeDto
import com.example.api.controller.horse.CoatColorDto
import com.example.api.controller.horse.DnaParentageResultDto
import com.example.api.controller.horse.SexDto
import com.example.api.controller.horse.toDomain
import java.time.LocalDate
import java.util.UUID

/**
 * `POST /api/bloodHorses` のリクエストボディ。
 *
 * 登録申請フォームに相当する。enum 項目（性・毛色・品種・DNA 判定）は HTTP 契約専用の `〜Dto` enum で受け取り、列挙子名で デシリアライズする。未知の値は Jackson
 * のデシリアライズで弾かれ `GlobalExceptionHandler` が 400 を返す。ドメイン enum への 変換は [toCommand] が [toDomain]
 * マッピングで行う（wire 契約とドメインの結合を断つ。詳細は `BloodHorseWireEnums.kt`）。 VO
 * で表す項目（番号・マイクロチップ・生産者）は素の文字列で受け取り、ユースケースで検証する。
 *
 * @property sireId 父（雄）の軽種馬ID
 * @property damId 母（雌）の軽種馬ID
 * @property sex 性
 * @property coatColor 毛色
 * @property breedType 品種
 * @property dateOfBirth 生年月日
 * @property breeder 生産者名
 * @property microchipNumber マイクロチップ番号
 * @property dnaParentage 申告された父母との DNA 型による親子判定結果
 * @property registrationNumber 交付される血統登録番号
 */
@Suppress("LongParameterList") // 登録申請フォーム相当の境界入力であり、項目の分割はかえって意味を損なう
data class RegisterBloodHorseRequest(
    val sireId: UUID,
    val damId: UUID,
    val sex: SexDto,
    val coatColor: CoatColorDto,
    val breedType: BreedTypeDto,
    val dateOfBirth: LocalDate,
    val breeder: String,
    val microchipNumber: String,
    val dnaParentage: DnaParentageResultDto,
    val registrationNumber: String,
)

/** リクエストボディを血統登録ユースケースの入力コマンドへ変換する。境界 DTO ↔ コマンドのフィールド対応はここに集約する。 */
fun RegisterBloodHorseRequest.toCommand(): RegisterInStudBookCommand =
    RegisterInStudBookCommand(
        sireId = sireId,
        damId = damId,
        sex = sex.toDomain(),
        coatColor = coatColor.toDomain(),
        breedType = breedType.toDomain(),
        dateOfBirth = dateOfBirth,
        breeder = breeder,
        microchipNumber = microchipNumber,
        dnaParentage = dnaParentage.toDomain(),
        registrationNumber = registrationNumber,
    )
