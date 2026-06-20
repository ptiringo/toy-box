package com.example.api.controller.racing

import com.example.api.application.horseracing.racing.RegisterAsRacehorseUseCase
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
 * 競走馬登録リソースの HTTP アダプター。
 *
 * Google AIP のリソース指向設計に従い、コレクション `/api/racing_registrations` に対する Create（競走馬登録）を 提供する。エラーレスポンスは RFC
 * 9457 (Problem Details) 形式で返す。
 */
@RestController
class RacingRegistrationController(
    private val registerAsRacehorse: RegisterAsRacehorseUseCase,
    private val clock: Clock,
) {
    @Operation(
        summary = "馬を競走馬登録する",
        description = "血統登録・馬名登録済みの馬を競走馬登録し、成立した登録を返す。業務ルール違反時は RFC 9457 形式の problem+json を返す。",
        tags = ["RacingRegistration"],
        responses =
            [
                ApiResponse(
                    responseCode = "201",
                    description = "競走馬登録成功（登録された競走馬登録リソースを返す）",
                    content =
                        [
                            Content(
                                schema = Schema(implementation = RacingRegistrationResponse::class),
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                            )
                        ],
                ),
                ApiResponse(
                    responseCode = "400",
                    description = "入力値が不正（登録番号など）",
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
                    description = "対象馬が存在しない、または前提条件（馬名登録済み）を満たさない",
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
    @PostMapping("/api/racing_registrations")
    fun register(
        @RequestBody request: RegisterRacingRegistrationRequest
    ): RacingRegistrationResponse =
        registerAsRacehorse(Command.now(request.toCommand(), clock))
            .mapError { it.toProblemDetail() }
            .orThrowProblem()
            .toResponse()
}
