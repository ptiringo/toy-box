package com.example.api.controller.horse

import com.example.api.application.studbook.horse.NameHorseUseCase
import com.example.api.application.studbook.horse.RegisterImportedHorseUseCase
import com.example.api.application.studbook.horse.RegisterInStudBookUseCase
import com.example.api.controller.horse.problem.toProblemDetail
import com.example.api.controller.horse.request.RegisterBloodHorseRequest
import com.example.api.controller.horse.request.RegisterHorseNameRequest
import com.example.api.controller.horse.request.RegisterImportedHorseRequest
import com.example.api.controller.horse.request.toCommand
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
 * 軽種馬リソースの HTTP アダプター。
 *
 * Google AIP のリソース指向設計に従い、コレクション `/api/bloodHorses` に対する Create（内国産馬の血統登録）と、 個体への カスタムメソッド
 * `:registerName`（馬名登録、[AIP-136](https://google.aip.dev/136)）を提供する。 父母不明の輸入馬は前提条件が
 * 大きく異なるため、コレクションへのカスタムメソッド `:registerImported`（[AIP-136](https://google.aip.dev/136)）として
 * 登録経路を分ける。エラーレスポンスは RFC 9457 (Problem Details) 形式で返す。
 */
@RestController
class BloodHorseController(
    private val registerInStudBook: RegisterInStudBookUseCase,
    private val registerImportedHorse: RegisterImportedHorseUseCase,
    private val nameHorse: NameHorseUseCase,
    private val clock: Clock,
) {
    @Operation(
        summary = "軽種馬を血統登録する",
        description = "父母を指定して血統登録を行い、誕生した軽種馬を返す。業務ルール違反時は RFC 9457 形式の problem+json を返す。",
        tags = ["BloodHorse"],
        responses =
            [
                ApiResponse(
                    responseCode = "201",
                    description = "血統登録成功（登録された軽種馬リソースを返す）",
                    content =
                        [
                            Content(
                                schema = Schema(implementation = BloodHorseResponse::class),
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
    @PostMapping("/api/bloodHorses")
    fun register(@RequestBody request: RegisterBloodHorseRequest): BloodHorseResponse =
        registerInStudBook(Command.now(request.toCommand(), clock))
            .mapError { it.toProblemDetail() }
            .orThrowProblem()
            .toResponse()

    @Operation(
        summary = "父母不明の輸入馬を血統登録する",
        description =
            "父母が当システムに存在しない輸入馬・基礎輸入馬を、原産国・揚陸日とともに血統登録し、誕生した軽種馬を返す。" +
                "業務ルール違反時は RFC 9457 形式の problem+json を返す。",
        tags = ["BloodHorse"],
        responses =
            [
                ApiResponse(
                    responseCode = "201",
                    description = "血統登録成功（登録された軽種馬リソースを返す）",
                    content =
                        [
                            Content(
                                schema = Schema(implementation = BloodHorseResponse::class),
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                            )
                        ],
                ),
                ApiResponse(
                    responseCode = "400",
                    description = "入力値が不正（登録番号・マイクロチップ・生産者・原産国など）",
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
    @PostMapping("/api/bloodHorses:registerImported")
    fun registerImported(@RequestBody request: RegisterImportedHorseRequest): BloodHorseResponse =
        registerImportedHorse(Command.now(request.toCommand(), clock))
            .mapError { it.toProblemDetail() }
            .orThrowProblem()
            .toResponse()

    @Operation(
        summary = "軽種馬に馬名を登録する",
        description = "血統登録済みの軽種馬に馬名を付与する。二重命名や対象不在などの業務ルール違反時は RFC 9457 形式の problem+json を返す。",
        tags = ["BloodHorse"],
        responses =
            [
                ApiResponse(
                    responseCode = "200",
                    description = "馬名登録成功（更新後の軽種馬リソースを返す）",
                    content =
                        [
                            Content(
                                schema = Schema(implementation = BloodHorseResponse::class),
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                            )
                        ],
                ),
                ApiResponse(
                    responseCode = "400",
                    description = "馬名が不正（カタカナ2〜9文字でない）",
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
                    description = "命名対象の軽種馬が存在しない",
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
                    description = "対象が既に命名済み（二重命名）",
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
    @PostMapping("/api/bloodHorses/{bloodHorseId}:registerName")
    fun registerName(
        @PathVariable bloodHorseId: UUID,
        @RequestBody request: RegisterHorseNameRequest,
    ): BloodHorseResponse =
        nameHorse(Command.now(request.toCommand(bloodHorseId), clock))
            .mapError { it.toProblemDetail() }
            .orThrowProblem()
            .toResponse()
}
