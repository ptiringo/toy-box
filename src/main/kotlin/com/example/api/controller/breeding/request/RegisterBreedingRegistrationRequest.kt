package com.example.api.controller.breeding.request

import com.example.api.application.studbook.breeding.RegisterBreedingRegistrationCommand
import java.util.UUID

/**
 * `POST /api/breedingRegistrations` のリクエストボディ。血統登録済みの個体を繁殖の用に供するための繁殖登録 Create。
 *
 * 繁殖登録する個体は登録済み軽種馬の ID で参照する。繁殖登録番号は VO で表すため素の文字列で受け取り、ユースケースで 検証する。付与されるロール（種牡馬／繁殖牝馬）は対象個体の性から
 * 定まるため入力では受け取らない。
 *
 * @property bloodHorseId 繁殖登録する個体（血統登録済み）の軽種馬ID
 * @property registrationNumber 交付される繁殖登録番号
 */
data class RegisterBreedingRegistrationRequest(
    val bloodHorseId: UUID,
    val registrationNumber: String,
)

/** リクエストを繁殖登録ユースケースの入力コマンドへ変換する。 */
fun RegisterBreedingRegistrationRequest.toCommand(): RegisterBreedingRegistrationCommand =
    RegisterBreedingRegistrationCommand(
        bloodHorseId = bloodHorseId,
        registrationNumber = registrationNumber,
    )
