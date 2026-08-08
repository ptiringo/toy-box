package com.example.api.controller.world

import com.example.api.application.iam.world.CreateWorldCommand
import com.example.api.application.iam.world.CreateWorldUseCase
import com.example.api.application.iam.world.DeleteWorldCommand
import com.example.api.application.iam.world.DeleteWorldUseCase
import com.example.api.application.iam.world.ListWorldsQuery
import com.example.api.application.iam.world.ListWorldsUseCase
import com.example.api.application.iam.world.RenameWorldCommand
import com.example.api.application.iam.world.RenameWorldUseCase
import com.example.api.controller.CurrentAccount
import com.example.api.controller.orThrowProblem
import com.example.api.controller.world.problem.toProblemDetail
import com.example.api.controller.world.request.CreateWorldRequest
import com.example.api.controller.world.request.RenameWorldRequest
import com.example.api.domain.shared.AccountId
import com.example.api.domain.shared.Command
import com.example.api.domain.shared.WorldId
import com.github.michaelbull.result.mapError
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.parameters.RequestBody as OperationRequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponse
import java.time.Clock
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 世界リソースの HTTP アダプター。
 *
 * Google AIP の標準メソッド（List / Create / Update / Delete）をコレクション `/api/worlds` に提供する。
 * 一覧・更新・削除はいずれも「自分の世界」に閉じており、他人の世界を指したリクエストは 404 になる。
 */
@RestController
class WorldController(
    private val listWorlds: ListWorldsUseCase,
    private val createWorld: CreateWorldUseCase,
    private val renameWorld: RenameWorldUseCase,
    private val deleteWorld: DeleteWorldUseCase,
    private val clock: Clock,
) {
    @Operation(
        operationId = "listWorlds",
        summary = "自分の世界を一覧する",
        description = "ログイン中のアカウントが所有する世界を、作成順（id 昇順）で一覧する。",
        tags = ["World"],
        responses =
            [
                ApiResponse(
                    responseCode = "200",
                    description = "自分の世界一覧の取得成功",
                    content =
                        [
                            Content(
                                array =
                                    ArraySchema(
                                        schema = Schema(implementation = WorldResponse::class)
                                    ),
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                            )
                        ],
                )
            ],
    )
    @GetMapping("/api/worlds")
    fun list(@Parameter(hidden = true) @CurrentAccount accountId: AccountId): List<WorldResponse> =
        listWorlds(ListWorldsQuery(accountId)).map { it.toResponse() }

    @Operation(
        operationId = "createWorld",
        summary = "世界を作る",
        description = "新しい世界（セーブデータ）を作る。名前が不正、または同名の世界が既にある場合は失敗する。",
        tags = ["World"],
        responses =
            [
                ApiResponse(
                    responseCode = "201",
                    description = "作成成功",
                    content =
                        [
                            Content(
                                schema = Schema(implementation = WorldResponse::class),
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                            )
                        ],
                ),
                ApiResponse(
                    responseCode = "400",
                    description = "名前がブランクか 64 文字を超える",
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
                    description = "同名の世界が既にある",
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
    @PostMapping("/api/worlds")
    fun create(
        @Parameter(hidden = true) @CurrentAccount accountId: AccountId,
        @OperationRequestBody(description = "作成したい世界の名前") @RequestBody request: CreateWorldRequest,
    ): WorldResponse {
        val command = Command.now(CreateWorldCommand(request.name), clock)
        return createWorld(accountId, command)
            .mapError { it.toProblemDetail() }
            .orThrowProblem()
            .toResponse()
    }

    @Operation(
        operationId = "renameWorld",
        summary = "世界の名前を変える",
        description = "自分の世界の名前を変える。他人の世界、または存在しない世界を指すと 404 を返す。",
        tags = ["World"],
        responses =
            [
                ApiResponse(
                    responseCode = "200",
                    description = "改名成功",
                    content =
                        [
                            Content(
                                schema = Schema(implementation = WorldResponse::class),
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                            )
                        ],
                ),
                ApiResponse(
                    responseCode = "400",
                    description = "新しい名前がブランクか 64 文字を超える",
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
                    description = "改名対象の世界が存在しない（他人の世界を指した場合も含む）",
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
                    description = "同名の世界が既にある、または並行更新と競合した",
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
    @PatchMapping("/api/worlds/{worldId}")
    fun rename(
        @Parameter(hidden = true) @CurrentAccount accountId: AccountId,
        @Parameter(description = "改名対象の世界の生 UUID") @PathVariable worldId: UUID,
        @OperationRequestBody(description = "世界の新しい名前") @RequestBody request: RenameWorldRequest,
    ): WorldResponse {
        val command = Command.now(RenameWorldCommand(WorldId(worldId), request.name), clock)
        return renameWorld(accountId, command)
            .mapError { it.toProblemDetail() }
            .orThrowProblem()
            .toResponse()
    }

    @Operation(
        operationId = "deleteWorld",
        summary = "世界を削除する",
        description = "自分の世界を削除する。他人の世界、または存在しない世界を指すと 404 を返す。",
        tags = ["World"],
        responses =
            [
                ApiResponse(responseCode = "204", description = "削除成功"),
                ApiResponse(
                    responseCode = "404",
                    description = "削除対象の世界が存在しない（他人の世界を指した場合も含む）",
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
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/api/worlds/{worldId}")
    fun delete(
        @Parameter(hidden = true) @CurrentAccount accountId: AccountId,
        @Parameter(description = "削除対象の世界の生 UUID") @PathVariable worldId: UUID,
    ) {
        val command = Command.now(DeleteWorldCommand(WorldId(worldId)), clock)
        deleteWorld(accountId, command).mapError { it.toProblemDetail() }.orThrowProblem()
    }
}
