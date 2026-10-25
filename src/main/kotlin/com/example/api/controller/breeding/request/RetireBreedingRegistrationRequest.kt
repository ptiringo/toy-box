package com.example.api.controller.breeding.request

import com.example.api.application.studbook.breeding.RetireBreedingRegistrationCommand
import com.example.api.controller.breeding.RetirementReasonDto
import com.example.api.controller.breeding.toDomain
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate
import java.util.UUID

/**
 * `POST /api/worlds/{worldId}/breedingRegistrations/{breedingRegistrationId}:retire` のリクエストボディ。
 *
 * 血統書に記載する「供用停止の事由とその事由が発生した日」（登録規程実施基準・第15条第1項(3)）に相当する。事由は HTTP 契約専用の [RetirementReasonDto]
 * で受け取り（未知の値は Jackson のデシリアライズで弾かれ 400）、ドメインの事由へは [toCommand] で変換する。
 *
 * @property reason 供用停止の事由
 * @property occurredOn 事由が発生した日
 */
@Schema(description = "繁殖供用停止リクエスト")
data class RetireBreedingRegistrationRequest(
    val reason: RetirementReasonDto,
    val occurredOn: LocalDate,
)

/** リクエストを、パスで指定された繁殖登録に対する供用停止ユースケースの入力コマンドへ変換する。 */
fun RetireBreedingRegistrationRequest.toCommand(
    breedingRegistrationId: UUID
): RetireBreedingRegistrationCommand =
    RetireBreedingRegistrationCommand(
        breedingRegistrationId = breedingRegistrationId,
        reason = reason.toDomain(),
        occurredOn = occurredOn,
    )
