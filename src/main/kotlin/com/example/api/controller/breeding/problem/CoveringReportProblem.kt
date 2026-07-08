package com.example.api.controller.breeding.problem

import com.example.api.application.studbook.breeding.SubmitCoveringReportUseCaseError
import com.example.api.controller.problem
import com.example.api.domain.studbook.model.breeding.SubmitCoveringReportError
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail

/**
 * 種付成績報告提出（[SubmitCoveringReportUseCaseError]）の業務エラーを RFC 9457 の [ProblemDetail] に変換する。
 *
 * - 種牡馬の繁殖登録の不在・登録ロール違反は、整った入力だが意味的に処理できないため 422 Unprocessable Entity （参照はリクエストボディにあり、URL
 *   パス上の対象不在＝404 ではない。ADR-0021）
 * - 同一種牡馬×同一種付年の二重提出は状態の競合として 409 Conflict
 */
fun SubmitCoveringReportUseCaseError.toProblemDetail(): ProblemDetail =
    when (this) {
        is SubmitCoveringReportUseCaseError.StallionRegistrationNotFound ->
            problem(
                    status = HttpStatus.UNPROCESSABLE_CONTENT,
                    code = "stallion-registration-not-found",
                    title = "Stallion registration not found",
                    detail = "提出者として指定された種牡馬の繁殖登録が存在しません。",
                )
                .apply {
                    setProperty("stallion_breeding_registration_id", stallionBreedingRegistrationId)
                }
        is SubmitCoveringReportUseCaseError.PreconditionViolated -> cause.toProblemDetail()
    }

private fun SubmitCoveringReportError.toProblemDetail(): ProblemDetail =
    when (this) {
        SubmitCoveringReportError.NotStallion ->
            problem(
                status = HttpStatus.UNPROCESSABLE_CONTENT,
                code = "not-stallion",
                title = "Registration is not a stallion",
                detail = "提出者として指定された繁殖登録のロールが種牡馬ではありません。",
            )
        is SubmitCoveringReportError.AlreadySubmittedForYear ->
            problem(
                    status = HttpStatus.CONFLICT,
                    code = "covering-report-already-submitted-for-year",
                    title = "Covering report already submitted for the year",
                    detail = "この種牡馬には指定された種付年の種付成績報告が既に提出されています。",
                )
                .apply {
                    setProperty("covering_year", coveringYear.value)
                    setProperty("existing_covering_report_id", existingCoveringReportId.value)
                }
    }
