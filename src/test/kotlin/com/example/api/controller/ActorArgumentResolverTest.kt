package com.example.api.controller

import com.example.api.application.iam.world.WorldQueries
import com.example.api.domain.iam.model.account.Account
import com.example.api.domain.iam.model.account.AccountRepository
import com.example.api.domain.iam.model.account.SubjectId
import com.example.api.domain.shared.WorldId
import com.github.michaelbull.result.getOrThrow
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.servlet.HandlerMapping

/**
 * [ActorArgumentResolver] の解決本体（`resolveActor`）の単体テスト。
 *
 * パスの `{worldId}` の所有確認（所有していなければ 404）と、配線ミスを 403 / 404 に化けさせない fail-loud を押さえる。
 */
class ActorArgumentResolverTest {

    private val accounts = mockk<AccountRepository>()
    private val worlds = mockk<WorldQueries>()
    private val resolver = ActorArgumentResolver(accounts, worlds)

    @AfterEach
    fun clearSecurityContext() {
        SecurityContextHolder.clearContext()
    }

    private fun authenticateAs(subject: String) {
        val jwt =
            Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(subject)
                .claim("sub", subject)
                .build()
        SecurityContextHolder.getContext().authentication = JwtAuthenticationToken(jwt)
    }

    private fun account(subject: String): Account =
        Account.create(subject).getOrThrow { IllegalStateException("テスト用アカウントの生成に失敗: $it") }

    /** パス変数（`{worldId}`）を載せたリクエストを組む。`null` を渡すと変数そのものが無い状態になる。 */
    private fun requestWith(worldId: UUID?): NativeWebRequest {
        val request = mockk<NativeWebRequest>()
        val variables = worldId?.let { mapOf("worldId" to it.toString()) }
        every {
            request.getAttribute(
                HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
                RequestAttributes.SCOPE_REQUEST,
            )
        } returns variables
        return request
    }

    @Test
    fun `所有している世界を指していれば Actor を組める`() {
        authenticateAs("sub-actor")
        val account = account("sub-actor")
        val worldId = UUID.randomUUID()
        every { accounts.findBySubjectId(SubjectId("sub-actor")) } returns account
        every { worlds.existsOwnedBy(account.id, WorldId(worldId)) } returns true

        val actor = resolver.resolveActor(requestWith(worldId))

        assert(actor.accountId == account.id)
        assert(actor.worldId.value == worldId)
    }

    @Test
    fun `所有していない世界は WorldNotFoundException になる`() {
        authenticateAs("sub-intruder")
        val account = account("sub-intruder")
        val worldId = UUID.randomUUID()
        every { accounts.findBySubjectId(SubjectId("sub-intruder")) } returns account
        every { worlds.existsOwnedBy(account.id, WorldId(worldId)) } returns false

        assertThrows<WorldNotFoundException> { resolver.resolveActor(requestWith(worldId)) }
    }

    @Test
    fun `パスに worldId が無いエンドポイントでの使用は配線ミスとして落とす`() {
        authenticateAs("sub-misplaced")
        val account = account("sub-misplaced")
        every { accounts.findBySubjectId(SubjectId("sub-misplaced")) } returns account

        assertThrows<IllegalStateException> { resolver.resolveActor(requestWith(null)) }
    }

    @Test
    fun `未登録の subject は AccountNotProvisionedException になる`() {
        authenticateAs("sub-unknown")
        every { accounts.findBySubjectId(SubjectId("sub-unknown")) } returns null

        assertThrows<AccountNotProvisionedException> {
            resolver.resolveActor(requestWith(UUID.randomUUID()))
        }
    }

    @Test
    fun `認証が JWT でなければ配線ミスとして落とす`() {
        assertThrows<IllegalStateException> {
            resolver.resolveActor(requestWith(UUID.randomUUID()))
        }
    }
}
