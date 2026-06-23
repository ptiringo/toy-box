package com.example.api.controller.jockey

import com.example.api.application.horseracing.jockey.JockeyRegistrationUseCase
import com.example.api.application.horseracing.jockey.RegisterJockeyCommand
import com.example.api.controller.orThrowProblem
import com.example.api.domain.shared.Command
import com.github.michaelbull.result.mapError
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
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
 * ジョッキーリソースの HTTP アダプター。
 *
 * Google AIP のリソース指向設計に従い、コレクション `/api/jockeys` に対する Create を提供する。 エラーレスポンスは RFC 9457 (Problem
 * Details) 形式で返す。
 */
@RestController
class JockeyController(
    private val registerJockey: JockeyRegistrationUseCase,
    private val clock: Clock,
) {
    @Operation(
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
            ],
    )
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/api/jockeys")
    fun register(@RequestBody request: RegisterJockeyRequest): JockeyResponse {
        val command = Command.now(RegisterJockeyCommand(request.firstName, request.lastName), clock)
        val jockey = registerJockey(command).mapError { it.toProblemDetail() }.orThrowProblem()
        return jockey.toResponse()
    }
}
