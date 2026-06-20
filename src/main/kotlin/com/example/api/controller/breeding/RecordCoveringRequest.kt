package com.example.api.controller.breeding

import com.example.api.application.horseracing.breeding.RecordCoveringCommand
import java.time.LocalDate
import java.util.UUID

/**
 * `POST /api/breeding_results` のリクエストボディ。
 *
 * 繁殖成績報告書の「種付」欄に相当する。繁殖登録・種牡馬は登録済みのIDで参照する。VO で表す種付証明書番号は 素の文字列で受け取り、ユースケースで検証する。
 *
 * @property breedingRegistrationId 種付対象の繁殖牝馬の繁殖登録ID
 * @property stallionId 配合相手の種牡馬の軽種馬ID
 * @property coveringDate 種付日
 * @property certificateNumber 種付証明書番号
 */
data class RecordCoveringRequest(
    val breedingRegistrationId: UUID,
    val stallionId: UUID,
    val coveringDate: LocalDate,
    val certificateNumber: String,
)

/** リクエストボディを種付記録ユースケースの入力コマンドへ変換する。 */
fun RecordCoveringRequest.toCommand(): RecordCoveringCommand =
    RecordCoveringCommand(
        breedingRegistrationId = breedingRegistrationId,
        stallionId = stallionId,
        coveringDate = coveringDate,
        certificateNumber = certificateNumber,
    )
