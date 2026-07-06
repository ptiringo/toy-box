package com.example.api.controller.breeding.request

import com.example.api.application.studbook.breeding.SubmitCoveringReportCommand
import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

/**
 * 種付成績報告の提出リクエストボディ。
 *
 * 提出日時はボディでは受けず、サーバー時刻（コマンドの発生時刻）を提出日として扱う。
 *
 * @property stallionBreedingRegistrationId 提出する種牡馬の繁殖登録ID
 * @property coveringYear 報告対象の種付年
 */
@Schema(description = "種付成績報告提出リクエスト")
data class SubmitCoveringReportRequest(
    val stallionBreedingRegistrationId: UUID,
    val coveringYear: Int,
)

/** リクエストボディをユースケースの入力コマンドへ写す。 */
fun SubmitCoveringReportRequest.toCommand(): SubmitCoveringReportCommand =
    SubmitCoveringReportCommand(
        stallionBreedingRegistrationId = stallionBreedingRegistrationId,
        coveringYear = coveringYear,
    )
