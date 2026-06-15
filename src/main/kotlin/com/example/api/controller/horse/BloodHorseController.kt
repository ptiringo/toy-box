package com.example.api.controller.horse

import com.example.api.application.horseracing.horse.RegisterInStudBookUseCase
import com.example.api.controller.orThrowProblem
import com.example.api.domain.shared.Command
import com.github.michaelbull.result.mapError
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import java.time.Instant
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 軽種馬リソースの HTTP アダプター。
 *
 * Google AIP のリソース指向設計に従い、コレクション `/api/blood_horses` に対する Create（血統登録）を提供する。 エラーレスポンスは RFC 9457
 * (Problem Details) 形式で返す。
 */
@RestController
class BloodHorseController(private val registerInStudBook: RegisterInStudBookUseCase) {
    @Operation(
        summary = "軽種馬を血統登録する",
        description = "父母を指定して血統登録を行い、誕生した軽種馬を返す。業務ルール違反時は RFC 9457 形式の problem+json を返す。",
        tags = ["BloodHorse"],
        responses =
            [
                ApiResponse(
                    responseCode = "201",
                    description = "血統登録成功",
                    content =
                        [
                            Content(
                                schema = Schema(implementation = RegisterBloodHorseResponse::class),
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                            )
                        ],
                ),
                ApiResponse(
                    responseCode = "400",
                    description = "入力値が不正（登録番号・マイクロチップ・生産者など）",
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
                    description = "父母が存在しない、または前提条件（父=雄・母=雌・DNA 整合・品種整合）を満たさない",
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
    @PostMapping("/api/blood_horses")
    fun register(@RequestBody request: RegisterBloodHorseRequest): RegisterBloodHorseResponse =
        registerInStudBook(Command(request.toCommand(), Instant.now()))
            .mapError { it.toProblemDetail() }
            .orThrowProblem()
            .toRegisterResponse()
}
