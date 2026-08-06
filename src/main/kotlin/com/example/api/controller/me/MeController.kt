package com.example.api.controller.me

import com.example.api.application.iam.me.ProvisionMeCommand
import com.example.api.application.iam.me.ProvisionMeUseCase
import com.example.api.controller.me.problem.toProblemDetail
import com.example.api.controller.orThrowProblem
import com.example.api.domain.shared.Command
import com.github.michaelbull.result.mapError
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import java.time.Clock
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 利用者自身に関する HTTP アダプター。
 *
 * `:provision` は **`@CurrentAccount` を使えない**。アカウントが未登録の状態で叩かれるのが本来の用途で、 resolver は未登録なら 403
 * を投げるため。ここだけは検証済みトークンから直接 `sub` を取る。
 */
@RestController
class MeController(private val provisionMe: ProvisionMeUseCase, private val clock: Clock) {

    @Operation(
        operationId = "provisionMe",
        summary = "初回ログインのセットアップを行う",
        description = "検証済みトークンの subject に対応するアカウントを用意し、世界が 1 つも無ければ最初の世界を作る。冪等。",
        tags = ["Me"],
        responses =
            [
                ApiResponse(
                    responseCode = "200",
                    description = "セットアップ成功（既にセットアップ済みでも同じ結果を返す）",
                    content =
                        [
                            Content(
                                schema = Schema(implementation = MeResponse::class),
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                            )
                        ],
                ),
                ApiResponse(
                    responseCode = "409",
                    description = "並行する同一 subject のセットアップと競合した",
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
    @PostMapping("/api/me:provision")
    fun provision(authentication: JwtAuthenticationToken): MeResponse {
        val command = Command.now(ProvisionMeCommand(authentication.token.subject.orEmpty()), clock)
        val accountId = provisionMe(command).mapError { it.toProblemDetail() }.orThrowProblem()
        return MeResponse(accountId.value)
    }
}
