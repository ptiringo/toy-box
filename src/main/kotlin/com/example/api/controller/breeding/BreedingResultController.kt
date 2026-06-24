package com.example.api.controller.breeding

import com.example.api.application.studbook.breeding.RecordCoveringUseCase
import com.example.api.application.studbook.breeding.RecordUncoveredUseCase
import com.example.api.application.studbook.breeding.ReportFoalingCommand
import com.example.api.application.studbook.breeding.ReportFoalingUseCase
import com.example.api.controller.breeding.problem.toProblemDetail
import com.example.api.controller.breeding.request.RecordBreedingResultRequest
import com.example.api.controller.breeding.request.ReportFoalingRequest
import com.example.api.controller.breeding.request.toCoveringCommand
import com.example.api.controller.breeding.request.toOutcome
import com.example.api.controller.breeding.request.toUncoveredCommand
import com.example.api.controller.orThrowProblem
import com.example.api.domain.shared.Command
import com.github.michaelbull.result.mapError
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
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
 * Google AIP のリソース指向設計に従い、コレクション `/api/breedingResults` に対する Create（年次レコードの起票）と、 個体へのカスタムメソッド
 * `:reportFoaling`（分娩結果報告、[AIP-136](https://google.aip.dev/136)）を提供する。Create は
 * 様式第14号の年次成績の2種類（種付した年・種付しなかった年）を、リクエストの `covering` の有無で判別する単一の Create として受ける。エラーレスポンスは RFC 9457
 * (Problem Details) 形式で返す。
 */
@RestController
class BreedingResultController(
    private val recordCovering: RecordCoveringUseCase,
    private val recordUncovered: RecordUncoveredUseCase,
    private val reportFoaling: ReportFoalingUseCase,
    private val clock: Clock,
) {
    @Operation(
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
    fun record(@RequestBody request: RecordBreedingResultRequest): BreedingResultResponse {
        val covering = request.covering
        return if (covering != null) {
            recordCovering(Command.now(request.toCoveringCommand(covering), clock))
                .mapError { it.toProblemDetail() }
                .orThrowProblem()
                .toResponse()
        } else {
            val command = request.toUncoveredCommand().orThrowProblem()
            recordUncovered(Command.now(command, clock))
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
                    description = "既に分娩結果が報告済み（二重報告）",
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
        @PathVariable breedingResultId: UUID,
        @RequestBody request: ReportFoalingRequest,
    ): BreedingResultResponse {
        val outcome = request.toOutcome().orThrowProblem()
        return reportFoaling(Command.now(ReportFoalingCommand(breedingResultId, outcome), clock))
            .mapError { it.toProblemDetail() }
            .orThrowProblem()
            .toResponse()
    }
}
