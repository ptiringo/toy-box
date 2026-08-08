package com.example.api.controller

import com.example.api.application.iam.world.WorldQueries
import com.example.api.application.racing.jockey.GetJockeyUseCase
import com.example.api.application.racing.jockey.JockeyRegistrationError
import com.example.api.application.racing.jockey.JockeyRegistrationUseCase
import com.example.api.application.racing.jockey.RegisterJockeyCommand
import com.example.api.config.ClockConfiguration
import com.example.api.controller.jockey.JockeyController
import com.example.api.domain.iam.model.account.AccountRepository
import com.example.api.domain.racing.model.jockey.JockeyId
import com.example.api.domain.shared.AccountId
import com.example.api.domain.shared.Actor
import com.example.api.domain.shared.Command
import com.example.api.domain.shared.WorldId
import com.example.api.domain.shared.generateId
import com.github.michaelbull.result.Err
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import java.util.UUID
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.TestConstructor
import org.springframework.test.context.TestConstructor.AutowireMode
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.assertj.MockMvcTester

/**
 * [GlobalExceptionHandler] の検証。
 *
 * 業務ルール違反ではない例外（リクエストボディ不正・想定外例外）が RFC 9457 形式で返ることを、 [JockeyController] を踏み台にして確認する。
 */
@WebMvcTest(JockeyController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ClockConfiguration::class)
@TestConstructor(autowireMode = AutowireMode.ALL)
class GlobalExceptionHandlerTest(val mockMvc: MockMvc) {
    @MockkBean private lateinit var registerJockey: JockeyRegistrationUseCase

    @MockkBean private lateinit var getJockey: GetJockeyUseCase

    // WebMvcConfig（CurrentAccountArgumentResolver）が全 @WebMvcTest スライスへ自動で載るため必要（本テストの検証対象ではない）。
    @MockkBean private lateinit var accounts: AccountRepository
    @MockkBean private lateinit var worldQueries: WorldQueries

    @MockkBean private lateinit var actorArgumentResolver: ActorArgumentResolver

    private val actor = Actor(accountId = AccountId(generateId()), worldId = WorldId(generateId()))

    /**
     * `WebMvcConfig` は `WebMvcConfigurer` なので `ActorArgumentResolver` は全スライスに載る。slice は認証フィルタを
     * 無効化しているため実解決は走らせず、固定の [actor] を返すよう差し替える（#704）。
     */
    @BeforeEach
    fun stubActor() {
        every { actorArgumentResolver.supportsParameter(any()) } returns true
        every { actorArgumentResolver.resolveArgument(any(), any(), any(), any()) } returns actor
    }

    private val tester = MockMvcTester.create(mockMvc)

    @Test
    fun `必須フィールド欠落のリクエストボディで 400 と規約付与済みの problem+json が返ること`() {
        // last_name が欠落しており Jackson のデシリアライズに失敗する。
        // フレームワーク標準例外由来でも funnel で error_code 規約が付与される。
        tester
            .post()
            .uri("/api/jockeys")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"first_name":"武"}""")
            .assertThat()
            .hasStatus(HttpStatus.BAD_REQUEST)
            .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
            .bodyJson()
            .extractingPath("$.error_code")
            .isEqualTo("bad-request")
    }

    @Test
    fun `業務エラー由来の problem が funnel を通っても自前の errorCode を保持し status 由来コードで上書きされないこと`() {
        // DuplicateJockey は problem() ＝ ConventionalProblemDetail で errorCode=duplicate-jockey を持つ。
        // funnel は型で規約済みと判定して触らないため、status(409) 由来の "conflict" に上書きされない。
        every { registerJockey(any<Actor>(), any<Command<RegisterJockeyCommand>>()) } returns
            Err(JockeyRegistrationError.DuplicateJockey(JockeyId(generateId())))

        tester
            .post()
            .uri("/api/jockeys")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"first_name":"武","last_name":"豊"}""")
            .assertThat()
            .hasStatus(HttpStatus.CONFLICT)
            .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
            .bodyJson()
            .extractingPath("$.error_code")
            .isEqualTo("duplicate-jockey")
    }

    @Test
    fun `トークンは正当だがアカウント未登録の例外発生時に 403 と規約付与済みの problem+json が返ること`() {
        // CurrentAccountArgumentResolver が投げる AccountNotProvisionedException を、
        // ユースケース呼び出し前に届く例外として模して funnel の描画を検証する。
        every { registerJockey(any<Actor>(), any<Command<RegisterJockeyCommand>>()) } throws
            AccountNotProvisionedException("test-subject")

        tester
            .post()
            .uri("/api/jockeys")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"first_name":"武","last_name":"豊"}""")
            .assertThat()
            .hasStatus(HttpStatus.FORBIDDEN)
            .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
            .bodyJson()
            .extractingPath("$.error_code")
            .isEqualTo("account-not-provisioned")

        tester
            .post()
            .uri("/api/jockeys")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"first_name":"武","last_name":"豊"}""")
            .assertThat()
            .bodyJson()
            .extractingPath("$.type")
            .isEqualTo("urn:problem-type:account-not-provisioned")
    }

    @Test
    fun `所有していない世界の指定で 404 と world-not-found の problem+json が返ること`() {
        val worldId = UUID.fromString("33333333-3333-3333-3333-333333333333")
        every { actorArgumentResolver.resolveArgument(any(), any(), any(), any()) } throws
            WorldNotFoundException(worldId)

        tester
            .post()
            .uri("/api/jockeys")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"first_name":"武","last_name":"豊"}""")
            .assertThat()
            .hasStatus(HttpStatus.NOT_FOUND)
            .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
            .bodyJson()
            .extractingPath("$.error_code")
            .isEqualTo("world-not-found")

        tester
            .post()
            .uri("/api/jockeys")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"first_name":"武","last_name":"豊"}""")
            .assertThat()
            .bodyJson()
            .extractingPath("$.world_id")
            .isEqualTo(worldId.toString())
    }

    @Test
    fun `想定外の例外発生時に 500 と problem+json が返ること`() {
        every { registerJockey(any<Actor>(), any<Command<RegisterJockeyCommand>>()) } throws
            RuntimeException("予期しない障害")

        tester
            .post()
            .uri("/api/jockeys")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"first_name":"武","last_name":"豊"}""")
            .assertThat()
            .hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
            .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
            .bodyJson()
            .extractingPath("$.error_code")
            .isEqualTo("internal-server-error")
    }
}
