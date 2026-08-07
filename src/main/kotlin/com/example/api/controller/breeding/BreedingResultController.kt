package com.example.api.controller.breeding

import com.example.api.application.studbook.breeding.RecordCoveringUseCase
import com.example.api.application.studbook.breeding.RecordUncoveredUseCase
import com.example.api.application.studbook.breeding.ReportFoalingCommand
import com.example.api.application.studbook.breeding.ReportFoalingUseCase
import com.example.api.application.studbook.breeding.SubmitBreedingReportCommand
import com.example.api.application.studbook.breeding.SubmitBreedingReportUseCase
import com.example.api.controller.CurrentActor
import com.example.api.controller.breeding.problem.toProblemDetail
import com.example.api.controller.breeding.request.RecordBreedingResultRequest
import com.example.api.controller.breeding.request.ReportFoalingRequest
import com.example.api.controller.breeding.request.toCoveringCommand
import com.example.api.controller.breeding.request.toOutcome
import com.example.api.controller.breeding.request.toUncoveredCommand
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
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 繁殖成績リソースの HTTP アダプター。
 *
 * Google AIP のリソース指向設計に従い、コレクション `/api/breedingResults` に対する Create と、 個体へのカスタムメソッド
 * `:reportFoaling`（分娩結果報告）・`:submitReport`（繁殖成績報告提出） （いずれも
 * [AIP-136](https://google.aip.dev/136)）を提供する。 Create は様式第14号の年次成績2種類をリクエストの `covering` 有無で判別する単一
 * Create として受ける。 エラーレスポンスは RFC 9457 (Problem Details) 形式で返す。
 */
@RestController
class BreedingResultController(
    private val recordCovering: RecordCoveringUseCase,
    private val recordUncovered: RecordUncoveredUseCase,
    private val reportFoaling: ReportFoalingUseCase,
    private val submitReport: SubmitBreedingReportUseCase,
    private val clock: Clock,
) {
    @Operation(
        operationId = "recordBreedingResult",
        summary = "繁殖成績の年次レコードを起こす（種付記録／種付せず）",
        description =
            "繁殖登録済みの牝馬の年次成績を起こす。リクエストの covering が非 null なら種付を記録し（分娩結果は未報告）、" +
                "null なら種付せず（その年に種付しなかった）を記録する。業務ルール違反時は RFC 9457 形式の problem+json を返す。",
        tags = ["BreedingResult"],
        responses =
            [
                ApiResponse(
                    responseCode = "201",
                    description = "記録成功（起票された繁殖成績リソースを返す）",
                    content =
                        [
                            Content(
                                schema = Schema(implementation = BreedingResultResponse::class),
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                            )
                        ],
                ),
                ApiResponse(
                    responseCode = "400",
                    description = "入力値が不正（種付証明書番号がブランク、種付せずなのに breeding_year が欠けるなど）",
                    content =
                        [
                            Content(
                                schema = Schema(implementation = ProblemDetail::class),
                                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            )
                        ],
                ),
                ApiResponse(
                    responseCode = "422",
                    description = "繁殖牝馬・種牡馬の繁殖登録が存在しない、または前提条件（登録ロール）を満たさない",
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
                    description = "同一繁殖牝馬・同一繁殖年に繁殖成績が既に記録されている（重複記録）",
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
    @PostMapping("/api/breedingResults")
    fun record(
        @CurrentActor actor: Actor,
        @OperationRequestBody(description = "起票する繁殖成績の年次レコード（種付記録／種付せず）")
        @RequestBody
        request: RecordBreedingResultRequest,
    ): BreedingResultResponse {
        val covering = request.covering
        return if (covering != null) {
            recordCovering(actor, Command.now(request.toCoveringCommand(covering), clock))
                .mapError { it.toProblemDetail() }
                .orThrowProblem()
                .toResponse()
        } else {
            val command = request.toUncoveredCommand().orThrowProblem()
            recordUncovered(actor, Command.now(command, clock))
                .mapError { it.toProblemDetail() }
                .orThrowProblem()
                .toResponse()
        }
    }

    @Operation(
        summary = "分娩結果を報告する",
        description =
            "種付済みの繁殖成績に分娩結果（生産または産駒なしの各区分）を報告し、更新後の繁殖成績リソースを返す。" +
                "二重報告や対象不在などの業務ルール違反時は RFC 9457 形式の problem+json を返す。",
        tags = ["BreedingResult"],
        responses =
            [
                ApiResponse(
                    responseCode = "200",
                    description = "分娩結果報告成功（更新後の繁殖成績リソースを返す）",
                    content =
                        [
                            Content(
                                schema = Schema(implementation = BreedingResultResponse::class),
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                            )
                        ],
                ),
                ApiResponse(
                    responseCode = "400",
                    description = "入力値が不正（生産なのに分娩日が欠けているなど）",
                    content =
                        [
                            Content(
                                schema = Schema(implementation = ProblemDetail::class),
                                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            )
                        ],
                ),
                ApiResponse(
                    responseCode = "404",
                    description = "報告対象の繁殖成績が存在しない",
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
                    description = "既に分娩結果が報告済み（二重報告）、または他の更新と競合した",
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
    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/api/breedingResults/{breedingResultId}:reportFoaling")
    fun reportFoaling(
        @CurrentActor actor: Actor,
        @Parameter(description = "分娩結果を報告する繁殖成績の生 UUID") @PathVariable breedingResultId: UUID,
        @OperationRequestBody(description = "報告する分娩結果") @RequestBody request: ReportFoalingRequest,
    ): BreedingResultResponse {
        val outcome = request.toOutcome().orThrowProblem()
        return reportFoaling(
                actor,
                Command.now(ReportFoalingCommand(breedingResultId, outcome), clock),
            )
            .mapError { it.toProblemDetail() }
            .orThrowProblem()
            .toResponse()
    }

    @Operation(
        summary = "繁殖成績報告を提出する",
        description =
            "繁殖成績報告書（様式第14号）の年次提出を記録し、更新後の繁殖成績リソースを返す。提出日時はサーバー時刻" +
                "（コマンドの発生時刻）を日本の暦日に写した提出日として扱う。期限（繁殖年の翌年5/31）超過の提出も" +
                "拒否せず受理し、期限超過（report_submitted_late）として応答に表れる。業務ルール違反時は RFC 9457 " +
                "形式の problem+json を返す。",
        tags = ["BreedingResult"],
        responses =
            [
                ApiResponse(
                    responseCode = "200",
                    description = "提出成功（更新後の繁殖成績リソースを返す。期限超過でも成功する）",
                    content =
                        [
                            Content(
                                schema = Schema(implementation = BreedingResultResponse::class),
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                            )
                        ],
                ),
                ApiResponse(
                    responseCode = "404",
                    description = "提出対象の繁殖成績が存在しない",
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
                    description = "既に提出済み（二重提出）、または他の更新と競合した",
                    content =
                        [
                            Content(
                                schema = Schema(implementation = ProblemDetail::class),
                                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            )
                        ],
                ),
                ApiResponse(
                    responseCode = "422",
                    description = "分娩結果が未確定のため提出できない",
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
    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/api/breedingResults/{breedingResultId}:submitReport")
    fun submitReport(
        @CurrentActor actor: Actor,
        @Parameter(description = "繁殖成績報告を提出する繁殖成績の生 UUID") @PathVariable breedingResultId: UUID,
    ): BreedingResultResponse =
        submitReport(actor, Command.now(SubmitBreedingReportCommand(breedingResultId), clock))
            .mapError { it.toProblemDetail() }
            .orThrowProblem()
            .toResponse()
}
