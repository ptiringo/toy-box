package com.example.api.controller.jockey

import com.example.api.application.racing.jockey.GetJockeyQuery
import com.example.api.application.racing.jockey.GetJockeyUseCase
import com.example.api.application.racing.jockey.JockeyRegistrationUseCase
import com.example.api.application.racing.jockey.RegisterJockeyCommand
import com.example.api.controller.CurrentActor
import com.example.api.controller.RequestFingerprint
import com.example.api.controller.jockey.problem.toProblemDetail
import com.example.api.controller.jockey.request.RegisterJockeyRequest
import com.example.api.controller.orThrowProblem
import com.example.api.domain.shared.Actor
import com.example.api.domain.shared.Command
import com.example.api.domain.shared.Idempotency
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
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * ジョッキーリソースの HTTP アダプター。
 *
 * Google AIP のリソース指向設計に従い、コレクション `/api/worlds/{worldId}/jockeys` に対する Create を提供する。 エラーレスポンスは RFC
 * 9457 (Problem Details) 形式で返す。
 *
 * 世界スコープ（`/api/worlds/{worldId}/...`、ADR-0067）配下に居る。ハンドラの `worldId` は OpenAPI に path parameter
 * を出すための宣言で、値の解決は `ActorArgumentResolver` がパスから行う。
 */
@RestController
class JockeyController(
    private val registerJockey: JockeyRegistrationUseCase,
    private val getJockey: GetJockeyUseCase,
    private val clock: Clock,
    private val requestFingerprint: RequestFingerprint,
) {
    @Operation(
        operationId = "registerJockey",
        summary = "ジョッキーを登録する",
        description = "ジョッキーを新規登録する。業務ルール違反時は RFC 9457 形式の problem+json を返す。",
        tags = ["Jockey"],
        responses =
            [
                ApiResponse(
                    responseCode = "201",
                    description = "登録成功",
                    content =
                        [
                            Content(
                                schema = Schema(implementation = JockeyResponse::class),
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                            )
                        ],
                ),
                ApiResponse(
                    responseCode = "400",
                    description = "氏名がブランク",
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
                    description = "同姓同名のジョッキーが既に登録済み",
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
                    description = "同じ Idempotency-Key が別内容のリクエストで再利用された",
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
    @PostMapping("/api/worlds/{worldId}/jockeys")
    fun register(
        @Parameter(description = "操作対象の世界のID") @PathVariable worldId: UUID,
        @Parameter(hidden = true) @CurrentActor actor: Actor,
        @Parameter(description = "再送を識別する冪等キー（同じキー・同じ内容の再送は初回と同じ結果を返す）")
        @RequestHeader(name = "Idempotency-Key", required = false)
        idempotencyKey: String?,
        @OperationRequestBody(description = "登録するジョッキーの氏名")
        @RequestBody
        request: RegisterJockeyRequest,
    ): JockeyResponse {
        val command =
            Command.now(
                RegisterJockeyCommand(request.firstName, request.lastName),
                clock,
                idempotencyKey?.let { Idempotency(it, requestFingerprint.of(request)) },
            )
        val jockey =
            registerJockey(actor, command).mapError { it.toProblemDetail() }.orThrowProblem()
        return jockey.toResponse()
    }

    @Operation(
        operationId = "getJockey",
        summary = "ジョッキーを取得する",
        description = "ID でジョッキーを取得する。対象が存在しなければ RFC 9457 形式の problem+json を返す。",
        tags = ["Jockey"],
        responses =
            [
                ApiResponse(
                    responseCode = "200",
                    description = "取得成功",
                    content =
                        [
                            Content(
                                schema = Schema(implementation = JockeyResponse::class),
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                            )
                        ],
                ),
                ApiResponse(
                    responseCode = "404",
                    description = "指定 ID のジョッキーが存在しない",
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
    @GetMapping("/api/worlds/{worldId}/jockeys/{id}")
    fun get(
        @Parameter(description = "操作対象の世界のID") @PathVariable worldId: UUID,
        @Parameter(hidden = true) @CurrentActor actor: Actor,
        @Parameter(description = "取得するジョッキーの生 UUID") @PathVariable id: UUID,
    ): JockeyResponse {
        val view =
            getJockey(actor, GetJockeyQuery(id)).mapError { it.toProblemDetail() }.orThrowProblem()
        return view.toResponse()
    }
}
