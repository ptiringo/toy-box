package com.example.api.controller.breeding

import com.example.api.application.studbook.breeding.RegisterBreedingRegistrationUseCase
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
 * 繁殖登録リソースの HTTP アダプター。
 *
 * Google AIP のリソース指向設計に従い、コレクション `/api/breedingRegistrations` に対する Create（血統登録済みの個体を
 * 繁殖の用に供するための繁殖登録）を提供する。繁殖の書き込み経路（種付記録・種付せず・分娩報告・供用停止）はいずれも ここで起こした繁殖登録を起点にする。エラーレスポンスは RFC 9457
 * (Problem Details) 形式で返す。
 */
@RestController
class BreedingRegistrationController(
    private val registerBreedingRegistration: RegisterBreedingRegistrationUseCase,
    private val clock: Clock,
) {
    @Operation(
        summary = "軽種馬を繁殖登録する",
        description =
            "血統登録済みの軽種馬を繁殖の用に供するため繁殖登録し、繁殖登録リソースを返す。担うロール（種牡馬／繁殖牝馬）は" +
                "個体の性から定まる。業務ルール違反時は RFC 9457 形式の problem+json を返す。",
        tags = ["BreedingRegistration"],
        responses =
            [
                ApiResponse(
                    responseCode = "201",
                    description = "繁殖登録成功（登録された繁殖登録リソースを返す）",
                    content =
                        [
                            Content(
                                schema =
                                    Schema(implementation = BreedingRegistrationResponse::class),
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                            )
                        ],
                ),
                ApiResponse(
                    responseCode = "400",
                    description = "入力値が不正（繁殖登録番号がブランク）",
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
                    description = "繁殖登録の対象として指定された軽種馬が存在しない",
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
    @PostMapping("/api/breedingRegistrations")
    fun register(
        @RequestBody request: RegisterBreedingRegistrationRequest
    ): BreedingRegistrationResponse =
        registerBreedingRegistration(Command.now(request.toCommand(), clock))
            .mapError { it.toProblemDetail() }
            .orThrowProblem()
            .toResponse()
}
