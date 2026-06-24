package com.example.api.controller.breeding

import com.example.api.application.studbook.breeding.RegisterBreedingRegistrationCommand
import java.util.UUID

/**
 * `POST /api/breedingRegistrations` のリクエストボディ。繁殖登録の Create。
 *
 * 繁殖登録は雄雌共通の単一の登録で、担うロール（種牡馬／繁殖牝馬）は対象個体の性から定まる。そのため境界では 対象個体の軽種馬IDと交付される繁殖登録番号だけを受け取り、ロールはユースケースが
 * 個体の性から導出する。
 *
 * @property bloodHorseId 繁殖登録する個体（血統登録済み）の軽種馬ID
 * @property registrationNumber 交付される繁殖登録番号
 */
data class RegisterBreedingRegistrationRequest(
    val bloodHorseId: UUID,
    val registrationNumber: String,
)

/** 繁殖登録の入力を繁殖登録ユースケースの入力コマンドへ変換する。 */
fun RegisterBreedingRegistrationRequest.toCommand(): RegisterBreedingRegistrationCommand =
    RegisterBreedingRegistrationCommand(
        bloodHorseId = bloodHorseId,
        registrationNumber = registrationNumber,
    )
