package com.example.api.controller.inspection

import com.example.api.application.studbook.inspection.FindHorseInspectionQuery
import com.example.api.application.studbook.inspection.FindHorseInspectionUseCase
import com.example.api.application.studbook.inspection.RecordHorseInspectionUseCase
import com.example.api.controller.inspection.problem.toProblemDetail
import com.example.api.controller.inspection.request.RecordHorseInspectionRequest
import com.example.api.controller.inspection.request.toCommand
import com.example.api.controller.orThrowProblem
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
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 審査リソースの HTTP アダプター（#484）。
 *
 * Google AIP のリソース指向設計に従い、コレクション `/api/horseInspections` に対する Create（審査の記録）と
 * Get（審査の参照）を提供する。血統登録の内部で審査を生成する既存経路（`RegisterInStudBookUseCase`）は そのままに、審査を単独で記録・参照できる対外 API
 * 経路を足す。エラーレスポンスは RFC 9457 (Problem Details) 形式で返す。
 */
@RestController
class HorseInspectionController(
    private val recordHorseInspection: RecordHorseInspectionUseCase,
    private val findHorseInspection: FindHorseInspectionUseCase,
    private val clock: Clock,
) {
    @Operation(
        operationId = "recordHorseInspection",
        summary = "審査を記録する",
        description = "確定済みの審査（個体識別・親子判定）を記録する。業務ルール違反時は RFC 9457 形式の problem+json を返す。",
        tags = ["HorseInspection"],
        responses =
            [
                ApiResponse(
                    responseCode = "201",
                    description = "記録成功（記録された審査リソースを返す）",
                    content =
                        [
                            Content(
                                schema = Schema(implementation = HorseInspectionResponse::class),
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                            )
                        ],
                ),
                ApiResponse(
                    responseCode = "400",
                    description = "入力値が不正（マイクロチップ番号・親子判定区分など）",
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
    @PostMapping("/api/horseInspections")
    fun record(
        @OperationRequestBody(description = "記録する審査（個体識別・親子判定）")
        @RequestBody
        request: RecordHorseInspectionRequest
    ): HorseInspectionResponse =
        recordHorseInspection(Command.now(request.toCommand(), clock))
            .mapError { it.toProblemDetail() }
            .orThrowProblem()
            .toResponse()

    @Operation(
        operationId = "getHorseInspection",
        summary = "審査を取得する",
        description = "ID で審査を取得する。対象が存在しなければ RFC 9457 形式の problem+json を返す。",
        tags = ["HorseInspection"],
        responses =
            [
                ApiResponse(
                    responseCode = "200",
                    description = "取得成功",
                    content =
                        [
                            Content(
                                schema = Schema(implementation = HorseInspectionResponse::class),
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                            )
                        ],
                ),
                ApiResponse(
                    responseCode = "404",
                    description = "指定 ID の審査が存在しない",
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
    @GetMapping("/api/horseInspections/{id}")
    fun get(
        @Parameter(description = "取得する審査の生 UUID") @PathVariable id: UUID
    ): HorseInspectionResponse =
        findHorseInspection(FindHorseInspectionQuery(id))
            .mapError { it.toProblemDetail() }
            .orThrowProblem()
            .toResponse()
}
