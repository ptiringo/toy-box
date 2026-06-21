package com.example.api.controller.breeding

import com.example.api.application.horseracing.breeding.RecordCoveringCommand
import java.time.LocalDate
import java.util.UUID

/**
 * `POST /api/breedingResults` のリクエストボディ。
 *
 * 繁殖成績報告書の「種付」欄に相当する。繁殖牝馬・種牡馬はいずれも登録済みの繁殖登録IDで参照する。VO で表す種付証明書 番号は素の文字列で受け取り、ユースケースで検証する。
 *
 * @property breedingRegistrationId 種付対象の繁殖牝馬の繁殖登録ID
 * @property stallionRegistrationId 配合相手の種牡馬の繁殖登録ID
 * @property coveringDate 種付日
 * @property certificateNumber 種付証明書番号
 */
data class RecordCoveringRequest(
    val breedingRegistrationId: UUID,
    val stallionRegistrationId: UUID,
    val coveringDate: LocalDate,
    val certificateNumber: String,
)

/** リクエストボディを種付記録ユースケースの入力コマンドへ変換する。 */
fun RecordCoveringRequest.toCommand(): RecordCoveringCommand =
    RecordCoveringCommand(
        breedingRegistrationId = breedingRegistrationId,
        stallionRegistrationId = stallionRegistrationId,
        coveringDate = coveringDate,
        certificateNumber = certificateNumber,
    )
