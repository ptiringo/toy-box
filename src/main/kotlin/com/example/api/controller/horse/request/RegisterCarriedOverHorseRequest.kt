package com.example.api.controller.horse.request

import com.example.api.application.studbook.horse.RegisterCarriedOverHorseCommand
import com.example.api.controller.horse.BreedTypeDto
import com.example.api.controller.horse.CoatColorDto
import com.example.api.controller.horse.SexDto
import com.example.api.controller.horse.toDomain
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

/**
 * `POST /api/worlds/{worldId}/bloodHorses:registerCarriedOver` のリクエストボディ。
 *
 * 先行する登録原簿に血統登録済みの馬をシステム境界で取り込む移行経路の入力（JAIRS への新規登録申請では ない。#633 /
 * ADR-0069）。内国産馬（[RegisterBloodHorseRequest]）と異なり父母 ID・DNA 判定を受け取らず、
 * 輸入馬（[RegisterImportedHorseRequest]）と異なり原産国・揚陸日も受け取らない。enum 項目は HTTP 契約専用の `〜Dto` enum で受け取り、VO
 * で表す項目は素の文字列で受け取ってユースケースで検証する。
 *
 * @property sex 性
 * @property coatColor 毛色
 * @property breedType 品種
 * @property dateOfBirth 生年月日
 * @property breeder 生産者名
 * @property microchipNumber マイクロチップ番号
 * @property registrationNumber 血統登録番号（先行原簿で交付済みの番号）
 */
@Schema(description = "移行取り込み血統登録リクエスト")
data class RegisterCarriedOverHorseRequest(
    val sex: SexDto,
    val coatColor: CoatColorDto,
    val breedType: BreedTypeDto,
    val dateOfBirth: LocalDate,
    val breeder: String,
    val microchipNumber: String,
    val registrationNumber: String,
)

/** リクエストボディを移行取り込み血統登録ユースケースの入力コマンドへ変換する。境界 DTO ↔ コマンドのフィールド対応はここに集約する。 */
fun RegisterCarriedOverHorseRequest.toCommand(): RegisterCarriedOverHorseCommand =
    RegisterCarriedOverHorseCommand(
        sex = sex.toDomain(),
        coatColor = coatColor.toDomain(),
        breedType = breedType.toDomain(),
        dateOfBirth = dateOfBirth,
        breeder = breeder,
        microchipNumber = microchipNumber,
        registrationNumber = registrationNumber,
    )
