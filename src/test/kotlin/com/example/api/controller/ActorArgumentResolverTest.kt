package com.example.api.controller

import com.example.api.application.iam.world.WorldQueries
import com.example.api.application.iam.world.WorldView
import com.example.api.domain.iam.model.account.Account
import com.example.api.domain.iam.model.account.AccountRepository
import com.example.api.domain.iam.model.account.SubjectId
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

/**
 * [ActorArgumentResolver] の解決本体（`resolveActor`）の単体テスト。
 *
 * 世界の解決は #704 時点の暫定実装（アカウントが持つ世界の先頭）なので、その振る舞いと、配線ミスを 403 に化けさせず落とす fail-loud を押さえる。
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

    @Test
    fun `アカウントと世界が揃っていれば Actor を組める`() {
        authenticateAs("sub-actor")
        val account = account("sub-actor")
        val worldId = UUID.randomUUID()
        every { accounts.findBySubjectId(SubjectId("sub-actor")) } returns account
        every { worlds.findAllByAccountId(account.id) } returns
            listOf(WorldView(id = worldId, name = "はじまりの世界"))

        val actor = resolver.resolveActor()

        assert(actor.accountId == account.id)
        assert(actor.worldId.value == worldId)
    }

    @Test
    fun `世界を複数持つ場合は先頭の世界を使う`() {
        authenticateAs("sub-multi")
        val account = account("sub-multi")
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        every { accounts.findBySubjectId(SubjectId("sub-multi")) } returns account
        every { worlds.findAllByAccountId(account.id) } returns
            listOf(
                WorldView(id = first, name = "はじまりの世界"),
                WorldView(id = second, name = "ふたつめの世界"),
            )

        val actor = resolver.resolveActor()

        assert(actor.worldId.value == first)
    }

    @Test
    fun `未登録の subject は AccountNotProvisionedException になる`() {
        authenticateAs("sub-unknown")
        every { accounts.findBySubjectId(SubjectId("sub-unknown")) } returns null

        assertThrows<AccountNotProvisionedException> { resolver.resolveActor() }
    }

    @Test
    fun `世界が 1 つも無いアカウントは配線ミスとして落とす`() {
        authenticateAs("sub-worldless")
        val account = account("sub-worldless")
        every { accounts.findBySubjectId(SubjectId("sub-worldless")) } returns account
        every { worlds.findAllByAccountId(account.id) } returns emptyList()

        assertThrows<IllegalStateException> { resolver.resolveActor() }
    }

    @Test
    fun `認証が JWT でなければ配線ミスとして落とす`() {
        assertThrows<IllegalStateException> { resolver.resolveActor() }
    }
}
