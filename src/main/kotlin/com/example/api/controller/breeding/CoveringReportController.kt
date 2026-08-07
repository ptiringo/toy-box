package com.example.api.controller.breeding

import com.example.api.application.studbook.breeding.SubmitCoveringReportUseCase
import com.example.api.controller.CurrentActor
import com.example.api.controller.breeding.problem.toProblemDetail
import com.example.api.controller.breeding.request.SubmitCoveringReportRequest
import com.example.api.controller.breeding.request.toCommand
import com.example.api.controller.orThrowProblem
import com.example.api.domain.shared.Actor
import com.example.api.domain.shared.Command
import com.github.michaelbull.result.mapError
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.parameters.RequestBody as OperationRequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponse
import java.time.Clock
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 種付成績報告リソースの HTTP アダプター。
 *
 * Google AIP のリソース指向設計に従い、コレクション `/api/coveringReports` に対する Create
 * （[AIP-133](https://google.aip.dev/133)）を提供する。牝側の繁殖成績報告（`:submitReport`）が既存リソースへの
 * 状態遷移＝カスタムメソッドであるのに対し、雄側は提出で初めてリソースが生まれるため標準の Create で表す。 エラーレスポンスは RFC 9457 (Problem Details)
 * 形式で返す。
 */
@RestController
class CoveringReportController(
    private val submitCoveringReport: SubmitCoveringReportUseCase,
    private val clock: Clock,
) {
    @Operation(
        summary = "種付成績報告を提出する",
        description =
            "種付成績報告書（様式第13号）の年次提出を記録し、提出された種付成績報告リソースを返す。提出日時は" +
                "サーバー時刻（コマンドの発生時刻）を日本の暦日に写した提出日として扱う。期限（種付年の当年9/30）" +
                "超過の提出も拒否せず受理し、期限超過（submitted_late）として応答に表れる。当年の種付記録が" +
                "1 件も無くても提出は受理される。業務ルール違反時は RFC 9457 形式の problem+json を返す。",
        tags = ["CoveringReport"],
        responses =
            [
                ApiResponse(
                    responseCode = "201",
                    description = "提出成功（提出された種付成績報告リソースを返す。期限超過でも成功する）",
                    content =
                        [
                            Content(
                                schema = Schema(implementation = CoveringReportResponse::class),
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                            )
                        ],
                ),
                ApiResponse(
                    responseCode = "422",
                    description = "種牡馬の繁殖登録が存在しない、または登録ロールが種牡馬でない",
                    content =
                        [
                            Content(
                                schema = Schema(implementation = ProblemDetail::class),
                                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            )
                        ],
                ),
                ApiResponse(
                    responseCode = "409",
                    description = "同一種牡馬・同一種付年の種付成績報告が既に提出されている（二重提出）",
                    content =
                        [
                            Content(
                                schema = Schema(implementation = ProblemDetail::class),
                                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            )
                        ],
                ),
            ],
    )
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/api/coveringReports")
    fun submit(
        @Parameter(hidden = true) @CurrentActor actor: Actor,
        @OperationRequestBody(description = "提出する種付成績報告（種牡馬の繁殖登録ID・種付年）")
        @RequestBody
        request: SubmitCoveringReportRequest,
    ): CoveringReportResponse =
        submitCoveringReport(actor, Command.now(request.toCommand(), clock))
            .mapError { it.toProblemDetail() }
            .orThrowProblem()
            .toResponse()
}
