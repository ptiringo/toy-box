package com.example.api.controller.horse.request

import com.example.api.application.studbook.horse.RegisterBroughtInFoalCommand
import com.example.api.controller.horse.BreedTypeDto
import com.example.api.controller.horse.CoatColorDto
import com.example.api.controller.horse.DnaParentageResultDto
import com.example.api.controller.horse.SexDto
import com.example.api.controller.horse.toDomain
import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

/**
 * `POST /api/worlds/{worldId}/bloodHorses:registerBroughtIn` のリクエストボディ。
 *
 * 持込馬（海外種付の産駒）の血統登録申請に相当する（#624 / ADR-0087）。父母・生年月日は受け取らず、報告済みの繁殖成績 （[breedingResultId]）から定める:
 * 父は海外種付の外国種牡馬、母は繁殖登録の繁殖牝馬、生年月日は分娩日。 内国産馬（[RegisterBloodHorseRequest]）と異なり父母 ID
 * を持ち込ませないのは、父が日本の軽種馬として存在しないためと、 繁殖成績に記録した種付と食い違う父母を申請できないようにするため。enum 項目は HTTP 契約専用の `〜Dto` enum
 * で受け取り、 VO で表す項目（番号・マイクロチップ・生産者）は素の文字列で受け取ってユースケースで検証する。
 *
 * @property breedingResultId 産駒が生じた繁殖成績ID（種付が海外種付で、分娩結果が生産として報告済みであること）
 * @property sex 性
 * @property coatColor 毛色
 * @property breedType 品種
 * @property breeder 生産者名
 * @property microchipNumber マイクロチップ番号
 * @property dnaParentage 申告された父母との DNA 型による親子判定結果
 * @property registrationNumber 交付される血統登録番号
 */
@Suppress("LongParameterList") // 登録申請フォーム相当の境界入力であり、項目の分割はかえって意味を損なう
@Schema(description = "持込馬血統登録リクエスト")
data class RegisterBroughtInFoalRequest(
    val breedingResultId: UUID,
    val sex: SexDto,
    val coatColor: CoatColorDto,
    val breedType: BreedTypeDto,
    val breeder: String,
    val microchipNumber: String,
    val dnaParentage: DnaParentageResultDto,
    val registrationNumber: String,
)

/** リクエストボディを持込馬血統登録ユースケースの入力コマンドへ変換する。境界 DTO ↔ コマンドのフィールド対応はここに集約する。 */
fun RegisterBroughtInFoalRequest.toCommand(): RegisterBroughtInFoalCommand =
    RegisterBroughtInFoalCommand(
        breedingResultId = breedingResultId,
        sex = sex.toDomain(),
        coatColor = coatColor.toDomain(),
        breedType = breedType.toDomain(),
        breeder = breeder,
        microchipNumber = microchipNumber,
        dnaParentage = dnaParentage.toDomain(),
        registrationNumber = registrationNumber,
    )
