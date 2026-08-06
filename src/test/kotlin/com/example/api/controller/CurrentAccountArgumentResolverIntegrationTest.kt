package com.example.api.controller

import com.example.api.domain.iam.model.account.AccountFixture
import com.example.api.domain.iam.model.account.AccountRepository
import com.example.api.domain.iam.model.account.SubjectId
import com.example.api.domain.shared.AccountId
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.test.context.TestConstructor
import org.springframework.test.context.TestConstructor.AutowireMode
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.assertj.MockMvcTester
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * [CurrentAccountArgumentResolver] が Spring MVC の実解決経路（`supportsParameter` →
 * `resolveArgument`）を通って実際に動くことを検証する。
 *
 * `CurrentAccountArgumentResolverTest` は `resolver.resolveAccountId()` を直接呼ぶユニットテストのため、
 * `supportsParameter` の型判定が空振りしていても検出できない （`AccountId` は `@JvmInline value class` で、通常のメソッド引数としては
 * JVM シグネチャ上 `UUID` へ 消去されるため `parameter.parameterType == AccountId::class.java` は常に false になる不具合が
 * 実際にあった。`.claude/rules/architecture.md`「ArchUnit で Kotlin の呼び出しを縛るときの空振り」と同種）。 ここでは
 * `@CurrentAccount accountId: AccountId` を実際に持つテスト専用のダミーコントローラを `@WebMvcTest` 経由で叩き、Spring
 * から見て解決が機能することを保証する。
 */
@WebMvcTest(CurrentAccountProbeController::class)
@AutoConfigureMockMvc(addFilters = false)
@TestConstructor(autowireMode = AutowireMode.ALL)
class CurrentAccountArgumentResolverIntegrationTest(val mockMvc: MockMvc) {
    @MockkBean private lateinit var accounts: AccountRepository

    private val tester = MockMvcTester.create(mockMvc)

    /** 他テストへの `SecurityContextHolder`（ThreadLocal）の漏れを防ぐ。 */
    @AfterEach
    fun clearSecurityContext() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `@CurrentAccount 付きハンドラ引数が Spring 経由で AccountId に解決されること`() {
        val account = AccountFixture.account(subjectId = "sub-integration")
        every { accounts.findBySubjectId(SubjectId("sub-integration")) } returns account
        val jwt =
            Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("sub-integration")
                .claim("sub", "sub-integration")
                .build()
        SecurityContextHolder.getContext().authentication = JwtAuthenticationToken(jwt)

        tester
            .get()
            .uri("/api/test/current-account-probe")
            .assertThat()
            .hasStatusOk()
            .bodyText()
            .isEqualTo(account.id.value.toString())
    }
}

/**
 * [CurrentAccountArgumentResolver] が Spring MVC 経由で実際に呼ばれることだけを確かめるための テスト専用コントローラ。本番のリソース設計（AIP
 * 準拠）の対象外。
 */
@RestController
class CurrentAccountProbeController {
    @GetMapping("/api/test/current-account-probe")
    fun probe(@CurrentAccount accountId: AccountId): String = accountId.value.toString()
}
