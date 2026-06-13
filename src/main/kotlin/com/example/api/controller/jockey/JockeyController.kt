package com.example.api.controller.jockey

import com.example.api.application.horseracing.jockey.JockeyRegistrationUseCase
import com.example.api.application.horseracing.jockey.RegisterJockeyCommand
import com.example.api.domain.shared.Command
import com.github.michaelbull.result.mapBoth
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import java.time.LocalDateTime
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/**
 * ジョッキーリソースの HTTP アダプター。
 *
 * Google AIP のリソース指向設計に従い、コレクション `/api/jockeys` に対する Create を提供する。 エラーレスポンスは RFC 9457 (Problem
 * Details) 形式で返す。
 */
@RestController
class JockeyController(private val registerJockey: JockeyRegistrationUseCase) {
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
                                schema = Schema(implementation = RegisterJockeyResponse::class),
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
    @PostMapping("/api/jockeys")
    fun register(@RequestBody request: RegisterJockeyRequest): ResponseEntity<Any> {
        val command =
            Command(RegisterJockeyCommand(request.firstName, request.lastName), LocalDateTime.now())
        return registerJockey(command)
            .mapBoth(
                success = { jockey ->
                    ResponseEntity.status(HttpStatus.CREATED)
                        .body<Any>(
                            RegisterJockeyResponse(
                                id = jockey.id.value,
                                firstName = jockey.firstName,
                                lastName = jockey.lastName,
                            )
                        )
                },
                failure = { error ->
                    val problem = error.toProblemDetail()
                    ResponseEntity.status(problem.status)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body<Any>(problem)
                },
            )
    }
}
