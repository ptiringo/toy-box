package com.example.api.controller.horse

import com.example.api.application.horseracing.horse.RegisterInStudBookCommand
import com.example.api.domain.horseracing.model.horse.bloodhorse.BreedType
import com.example.api.domain.horseracing.model.horse.bloodhorse.CoatColor
import com.example.api.domain.horseracing.model.horse.bloodhorse.DnaParentageResult
import com.example.api.domain.horseracing.model.horse.bloodhorse.Sex
import java.time.LocalDate
import java.util.UUID

/**
 * `POST /api/blood_horses` のリクエストボディ。
 *
 * 登録申請フォームに相当する。enum 項目（性・毛色・品種・DNA 判定）は列挙子名で受け取り、未知の値は Jackson の デシリアライズで弾かれ
 * `GlobalExceptionHandler` が 400 を返す。VO で表す項目（番号・マイクロチップ・生産者）は 素の文字列で受け取り、ユースケースで検証する。
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
    val sex: Sex,
    val coatColor: CoatColor,
    val breedType: BreedType,
    val dateOfBirth: LocalDate,
    val breeder: String,
    val microchipNumber: String,
    val dnaParentage: DnaParentageResult,
    val registrationNumber: String,
)

/** リクエストボディを血統登録ユースケースの入力コマンドへ変換する。境界 DTO ↔ コマンドのフィールド対応はここに集約する。 */
fun RegisterBloodHorseRequest.toCommand(): RegisterInStudBookCommand =
    RegisterInStudBookCommand(
        sireId = sireId,
        damId = damId,
        sex = sex,
        coatColor = coatColor,
        breedType = breedType,
        dateOfBirth = dateOfBirth,
        breeder = breeder,
        microchipNumber = microchipNumber,
        dnaParentage = dnaParentage,
        registrationNumber = registrationNumber,
    )
