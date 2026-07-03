package com.example.api.controller.inspection.request

import com.example.api.application.studbook.inspection.RecordHorseInspectionCommand
import com.example.api.controller.inspection.IdentificationFeaturesDto
import com.example.api.controller.inspection.ParentageDeterminationDto
import com.example.api.controller.inspection.toDomain

/**
 * `POST /api/horseInspections` のリクエストボディ。
 *
 * 審査の記録申請に相当する。親子判定は判別子 `type` 付きの [ParentageDeterminationDto] で受け、未知の 判別子は Jackson のデシリアライズで弾かれ
 * `GlobalExceptionHandler` が 400 を返す。マイクロチップ番号は VO で表す項目のため素の文字列で受け取り、ユースケースで検証する（ADR-0026）。
 *
 * @property microchipNumber マイクロチップ番号（15 桁数字）
 * @property parentage 親子判定
 * @property features 特徴記述子（任意）
 */
data class RecordHorseInspectionRequest(
    val microchipNumber: String,
    val parentage: ParentageDeterminationDto,
    val features: IdentificationFeaturesDto? = null,
)

/**
 * リクエストボディを審査記録ユースケースの入力コマンドへ変換する。境界 DTO ↔ コマンドのフィールド対応は ここに集約する。features の「全フィールド null → 不在」正規化は
 * [IdentificationFeaturesDto.toDomain] が担う。
 */
fun RecordHorseInspectionRequest.toCommand(): RecordHorseInspectionCommand =
    RecordHorseInspectionCommand(
        microchipNumber = microchipNumber,
        parentage = parentage.toDomain(),
        features = features?.toDomain(),
    )
