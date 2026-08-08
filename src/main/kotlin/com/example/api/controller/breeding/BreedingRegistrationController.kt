package com.example.api.controller.breeding

import com.example.api.application.studbook.breeding.RegisterBreedingRegistrationUseCase
import com.example.api.controller.CurrentActor
import com.example.api.controller.breeding.problem.toProblemDetail
import com.example.api.controller.breeding.request.RegisterBreedingRegistrationRequest
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
 * 繁殖登録リソースの HTTP アダプター。
 *
 * Google AIP のリソース指向設計に従い、コレクション `/api/worlds/{worldId}/breedingRegistrations` に対する Create（繁殖登録の成立）
 * を提供する。繁殖登録は 種付記録・種付せず・分娩報告・供用停止といった繁殖の書き込み経路の起点であり、この Create が その起点を開通させる。 エラーレスポンスは RFC 9457
 * (Problem Details) 形式で返す。
 *
 * 世界スコープ（`/api/worlds/{worldId}/...`、ADR-0067）配下に居る。ハンドラの `worldId` は OpenAPI に path parameter
 * を出すための宣言で、値の解決は `ActorArgumentResolver` がパスから行う。
 */
@RestController
class BreedingRegistrationController(
    private val registerBreedingRegistration: RegisterBreedingRegistrationUseCase,
    private val clock: Clock,
) {
    @Operation(
        operationId = "registerBreedingRegistration",
        summary = "繁殖登録を成立させる",
        description =
            "血統登録済みの個体を繁殖の用に供するための繁殖登録を成立させ、成立した繁殖登録リソースを返す。" +
                "付与されるロール（種牡馬／繁殖牝馬）は対象個体の性から定まる。業務ルール違反時は RFC 9457 形式の problem+json を返す。",
        tags = ["BreedingRegistration"],
        responses =
            [
                ApiResponse(
                    responseCode = "201",
                    description = "登録成功（成立した繁殖登録リソースを返す）",
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
                    description = "入力値が不正（繁殖登録番号がブランクなど）",
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
    @PostMapping("/api/worlds/{worldId}/breedingRegistrations")
    fun register(
        @Parameter(description = "操作対象の世界のID") @PathVariable worldId: UUID,
        @Parameter(hidden = true) @CurrentActor actor: Actor,
        @OperationRequestBody(description = "成立させる繁殖登録（対象個体・繁殖登録番号）")
        @RequestBody
        request: RegisterBreedingRegistrationRequest,
    ): BreedingRegistrationResponse =
        registerBreedingRegistration(actor, Command.now(request.toCommand(), clock))
            .mapError { it.toProblemDetail() }
            .orThrowProblem()
            .toResponse()
}
