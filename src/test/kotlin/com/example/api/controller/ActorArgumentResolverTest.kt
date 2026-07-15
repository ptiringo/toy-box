package com.example.api.controller

import com.example.api.application.iam.account.ResolveActorQuery
import com.example.api.application.iam.account.ResolveActorUseCase
import com.example.api.application.iam.account.ResolveActorUseCaseError
import com.example.api.domain.shared.AccountId
import com.example.api.domain.shared.Actor
import com.example.api.domain.shared.Permission
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.core.MethodParameter
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.ErrorResponseException
import org.springframework.web.context.request.NativeWebRequest

class ActorArgumentResolverTest {

    private val actor =
        Actor(
            AccountId(UUID.fromString("00000000-0000-7000-8000-000000000001")),
            setOf(Permission("studbook:horse:name")),
        )

    /** `Actor` を引数に取るハンドラの MethodParameter を作るためのダミー。 */
    @Suppress("UNUSED_PARAMETER") private fun handler(actor: Actor) = Unit

    private fun actorParameter(): MethodParameter =
        MethodParameter(
            ActorArgumentResolverTest::class.java.getDeclaredMethod("handler", Actor::class.java),
            0,
        )

    private fun authenticate(subject: String) {
        val jwt =
            Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(subject)
                .claim("sub", subject)
                .build()
        SecurityContextHolder.getContext().authentication = JwtAuthenticationToken(jwt)
    }

    @AfterEach
    fun clear() {
        SecurityContextHolder.clearContext()
    }

    @Nested
    inner class SuccessCase {
        @Test
        fun `Actor 型の引数を解決対象と判定する`() {
            val useCase = mockk<ResolveActorUseCase>()
            val resolver = ActorArgumentResolver(useCase)

            assert(resolver.supportsParameter(actorParameter()))
        }

        @Test
        fun `認証済みの sub からユースケースが組んだ Actor を返す`() {
            authenticate("idp-sub-001")
            val useCase =
                mockk<ResolveActorUseCase> {
                    every { this@mockk(ResolveActorQuery("idp-sub-001")) } returns Ok(actor)
                }
            val resolver = ActorArgumentResolver(useCase)

            val resolved =
                resolver.resolveArgument(actorParameter(), null, mockk<NativeWebRequest>(), null)

            assert(resolved == actor)
        }
    }

    @Nested
    inner class FailureCase {
        @Test
        fun `account 未登録の sub は 403 の ProblemDetail を投げる`() {
            authenticate("unknown-sub")
            val useCase =
                mockk<ResolveActorUseCase> {
                    every { this@mockk(any()) } returns
                        Err(ResolveActorUseCaseError.AccountNotFound("unknown-sub"))
                }
            val resolver = ActorArgumentResolver(useCase)

            val thrown =
                runCatching {
                        resolver.resolveArgument(
                            actorParameter(),
                            null,
                            mockk<NativeWebRequest>(),
                            null,
                        )
                    }
                    .exceptionOrNull()

            assert(thrown is ErrorResponseException)
            assert((thrown as ErrorResponseException).statusCode.value() == 403)
            assert(thrown.body.properties?.get("error_code") == "account-not-provisioned")
        }

        @Test
        fun `認証情報が無いときは fail-loud で IllegalStateException を投げる`() {
            // authenticate を呼ばない＝SecurityContext に認証が無い状態。authenticated なはずの
            // エンドポイントに認証が無いのは設定漏れなので、空の subject で DB を引かず 500 に落とす。
            val useCase = mockk<ResolveActorUseCase>()
            val resolver = ActorArgumentResolver(useCase)

            val thrown =
                runCatching {
                        resolver.resolveArgument(
                            actorParameter(),
                            null,
                            mockk<NativeWebRequest>(),
                            null,
                        )
                    }
                    .exceptionOrNull()

            assert(thrown is IllegalStateException)
        }
    }
}
