package com.example.api.controller

import com.example.api.domain.iam.model.account.AccountFixture
import com.example.api.domain.iam.model.account.AccountRepository
import com.example.api.domain.iam.model.account.SubjectId
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken

class CurrentAccountArgumentResolverTest {

    private val accounts = mockk<AccountRepository>()
    private val resolver = CurrentAccountArgumentResolver(accounts)

    /**
     * `SecurityContextHolder` はデフォルトで ThreadLocal 実装であり、Gradle のテスト実行はスレッドを
     * 使い回す。テストがここへ書き込んだ認証情報を残したままにすると、同一スレッド上で後に実行される 他のテストへ漏れ得るため、各テスト後に必ずクリアする。
     */
    @AfterEach
    fun clearSecurityContext() {
        SecurityContextHolder.clearContext()
    }

    private fun authenticateWith(subject: String) {
        val jwt =
            Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(subject)
                .claim("sub", subject)
                .build()
        SecurityContextHolder.getContext().authentication = JwtAuthenticationToken(jwt)
    }

    @Test
    fun `登録済みの subject なら AccountId を返す`() {
        val account = AccountFixture.account(subjectId = "sub-known", version = 1L)
        every { accounts.findBySubjectId(SubjectId("sub-known")) } returns account
        authenticateWith("sub-known")

        val resolved = resolver.resolveAccountId()

        assert(resolved == account.id)
    }

    @Test
    fun `未登録の subject なら AccountNotProvisionedException`() {
        every { accounts.findBySubjectId(SubjectId("sub-unknown")) } returns null
        authenticateWith("sub-unknown")

        var thrown = false
        try {
            resolver.resolveAccountId()
        } catch (_: AccountNotProvisionedException) {
            thrown = true
        }

        assert(thrown)
    }

    @Test
    fun `認証が無ければ IllegalStateException で fail-loud する`() {
        SecurityContextHolder.clearContext()

        var thrown = false
        try {
            resolver.resolveAccountId()
        } catch (_: IllegalStateException) {
            thrown = true
        }

        assert(thrown)
    }

    @Test
    fun `JWT でない認証も IllegalStateException で fail-loud する`() {
        SecurityContextHolder.getContext().authentication =
            TestingAuthenticationToken("someone", "credentials")

        var thrown = false
        try {
            resolver.resolveAccountId()
        } catch (_: IllegalStateException) {
            thrown = true
        }

        assert(thrown)
    }
}
