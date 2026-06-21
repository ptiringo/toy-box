package com.example.api.controller.horse

import com.example.api.application.horseracing.horse.NameHorseCommand
import java.util.UUID

/**
 * `POST /api/bloodHorses/{bloodHorseId}:registerName` のリクエストボディ。
 *
 * 馬名登録申請に相当する。命名対象の軽種馬IDは URL パスで指定するため、ボディは付与する馬名のみを持つ。 馬名は VO で検証するため素の文字列で受け取り、ユースケースで
 * [com.example.api.domain.horseracing.model.horse.bloodhorse.HorseName] の検証を通す。
 *
 * @property name 付与する馬名
 */
data class RegisterHorseNameRequest(val name: String)

/** リクエストボディと URL パスの軽種馬IDを馬名登録ユースケースの入力コマンドへ変換する。 */
fun RegisterHorseNameRequest.toCommand(bloodHorseId: UUID): NameHorseCommand =
    NameHorseCommand(bloodHorseId = bloodHorseId, name = name)
