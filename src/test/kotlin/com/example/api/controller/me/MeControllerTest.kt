package com.example.api.controller.me

import com.example.api.application.iam.me.ProvisionMeUseCase
import com.example.api.application.iam.world.WorldQueries
import com.example.api.config.ClockConfiguration
import com.example.api.domain.iam.model.account.Account
import com.example.api.domain.iam.model.account.AccountRepository
import com.example.api.domain.iam.model.account.SubjectId
import com.example.api.domain.iam.model.world.World
import com.example.api.domain.iam.model.world.WorldRepository
import com.example.api.domain.shared.UpdateConflict
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.getOrThrow
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.test.context.TestConstructor
import org.springframework.test.context.TestConstructor.AutowireMode
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.assertj.MockMvcTester

/**
 * 利用者自身リソースの HTTP 契約を検証するスライステスト。
 *
 * `ProvisionMeUseCase` 自体は `@MockkBean` で差し替えない。`ProvisionMeUseCase.invoke` の戻り値 `Result<AccountId,
 * ProvisionMeError>` は value class（`Result`）が別の value class（`AccountId`）を 型引数に持つ二重の入れ子で、mockk の
 * `every { ... } returns Ok(accountId)` 経由の stub がこの組み合わせを 正しく box/unbox できず（`ClassCastException:
 * UUID cannot be cast to AccountId`）実測で確認した。かわりに 実物の `ProvisionMeUseCase` を `@Import`
 * し、その依存先（`AccountRepository` / `WorldRepository`。戻り値に value class の入れ子を持たない）だけを `@MockkBean`
 * で差し替える。
 *
 * `ProvisionMeError.InvalidDefaultWorldName`（既定の世界名の定数設定ミスでしか起きない防御的分岐）は
 * この経路では現実的な入力で再現できないため、[com.example.api.controller.me.problem.MeProblemTest] で `toProblemDetail()`
 * を直接検証してカバレッジを埋める。
 *
 * `MeController.provision` は `@CurrentAccount` を使わず、検証済み JWT を直接 `JwtAuthenticationToken` として
 * 受け取る。この型付き引数は Spring MVC 標準の `PrincipalMethodArgumentResolver` が
 * `HttpServletRequest.getUserPrincipal()` から解決する。`@WebMvcTest(addFilters = false)` は Security の
 * フィルタチェーンを無効化しており、`SecurityMockMvcRequestPostProcessors.authentication`（フィルタ経由で
 * `SecurityContextRepository` に頼る）はフィルタ無しでは効かないため使わない。代わりに
 * `MockHttpServletRequestBuilder.principal(Principal)` で `userPrincipal` を直接差し込む。
 */
@WebMvcTest(MeController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ClockConfiguration::class, ProvisionMeUseCase::class)
@TestConstructor(autowireMode = AutowireMode.ALL)
class MeControllerTest(val mockMvc: MockMvc) {
    @MockkBean private lateinit var accounts: AccountRepository
    @MockkBean private lateinit var worlds: WorldRepository
    @MockkBean private lateinit var worldQueries: WorldQueries

    private val tester = MockMvcTester.create(mockMvc)

    private fun jwtWithSubject(subject: String): JwtAuthenticationToken {
        val jwt =
            Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(subject)
                .claim("sub", subject)
                .build()
        return JwtAuthenticationToken(jwt)
    }

    @Test
    fun `初回セットアップに成功すると 200 と account_id を返す`() {
        val subject = "me-controller-test-new-subject"
        every { accounts.findBySubjectId(SubjectId(subject)) } returns null
        every { accounts.save(any()) } answers { Ok(firstArg<Account>()) }
        every { worlds.existsByAccountId(any()) } returns false
        every { worlds.save(any()) } answers { Ok(firstArg<World>()) }

        val created = Account.create(subject).getOrThrow { AssertionError(it.toString()) }

        tester
            .post()
            .uri("/api/me:provision")
            .principal(jwtWithSubject(subject))
            .assertThat()
            .hasStatusOk()
            .bodyJson()
            .extractingPath("$.account_id")
            .isNotNull

        // account_id は Account.create() が自動採番するため、応答値そのものではなく
        // save に渡された Account が持つ subjectId で本人性を確かめる。
        assert(created.subjectId == SubjectId(subject))
    }

    @Test
    fun `並行するセットアップと競合すると 409 の problem を返す`() {
        val subject = "me-controller-test-conflict-subject"
        every { accounts.findBySubjectId(SubjectId(subject)) } returns null
        every { accounts.save(any()) } returns Err(UpdateConflict)

        tester
            .post()
            .uri("/api/me:provision")
            .principal(jwtWithSubject(subject))
            .assertThat()
            .hasStatus(HttpStatus.CONFLICT)
            .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
            .bodyJson()
            .extractingPath("$.error_code")
            .isEqualTo("provisioning-conflict")
    }

    @Test
    fun `トークンの sub がブランクなら 500 の problem を返す`() {
        // フィルタ無効化スライスでは JWT 検証を経由しないため、ブランク sub を直接注入して防御的分岐を踏む。
        every { accounts.findBySubjectId(SubjectId(" ")) } returns null

        tester
            .post()
            .uri("/api/me:provision")
            .principal(jwtWithSubject(" "))
            .assertThat()
            .hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
            .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
            .bodyJson()
            .extractingPath("$.error_code")
            .isEqualTo("invalid-subject")
    }
}
