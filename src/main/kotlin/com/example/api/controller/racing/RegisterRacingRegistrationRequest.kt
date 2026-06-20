package com.example.api.controller.racing

import com.example.api.application.horseracing.racing.RegisterAsRacehorseCommand
import java.util.UUID

/**
 * `POST /api/racing_registrations` のリクエストボディ。
 *
 * 競走馬登録申請フォームに相当する。対象馬は軽種馬IDで参照し、VO で表す登録番号は素の文字列で受け取って ユースケースで検証する。
 *
 * @property bloodHorseId 競走馬登録する馬の軽種馬ID
 * @property registrationNumber 交付される競走馬登録番号
 */
data class RegisterRacingRegistrationRequest(val bloodHorseId: UUID, val registrationNumber: String)

/** リクエストボディを競走馬登録ユースケースの入力コマンドへ変換する。 */
fun RegisterRacingRegistrationRequest.toCommand(): RegisterAsRacehorseCommand =
    RegisterAsRacehorseCommand(bloodHorseId = bloodHorseId, registrationNumber = registrationNumber)
