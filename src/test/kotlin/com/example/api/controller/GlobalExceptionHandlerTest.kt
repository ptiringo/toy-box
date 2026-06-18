package com.example.api.controller

import com.example.api.application.horseracing.jockey.JockeyRegistrationError
import com.example.api.application.horseracing.jockey.JockeyRegistrationUseCase
import com.example.api.application.horseracing.jockey.RegisterJockeyCommand
import com.example.api.config.ClockConfiguration
import com.example.api.controller.jockey.JockeyController
import com.example.api.domain.horseracing.model.jockey.JockeyId
import com.example.api.domain.shared.Command
import com.example.api.domain.shared.generateId
import com.github.michaelbull.result.Err
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.Test
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
@Import(ClockConfiguration::class)
@TestConstructor(autowireMode = AutowireMode.ALL)
class GlobalExceptionHandlerTest(val mockMvc: MockMvc) {
    @MockkBean private lateinit var registerJockey: JockeyRegistrationUseCase

    private val tester = MockMvcTester.create(mockMvc)

    @Test
    fun `必須フィールド欠落のリクエストボディで 400 と規約付与済みの problem+json が返ること`() {
        // lastName が欠落しており Jackson のデシリアライズに失敗する。
        // フレームワーク標準例外由来でも funnel で errorCode 規約が付与される。
        tester
            .post()
            .uri("/api/jockeys")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"firstName":"武"}""")
            .assertThat()
            .hasStatus(HttpStatus.BAD_REQUEST)
            .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
            .bodyJson()
            .extractingPath("$.errorCode")
            .isEqualTo("bad-request")
    }

    @Test
    fun `業務エラー由来の problem が funnel を通っても自前の errorCode を保持し status 由来コードで上書きされないこと`() {
        // DuplicateJockey は problem() ＝ ConventionalProblemDetail で errorCode=duplicate-jockey を持つ。
        // funnel は型で規約済みと判定して触らないため、status(409) 由来の "conflict" に上書きされない。
        every { registerJockey(any<Command<RegisterJockeyCommand>>()) } returns
            Err(JockeyRegistrationError.DuplicateJockey(JockeyId(generateId())))

        tester
            .post()
            .uri("/api/jockeys")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"firstName":"武","lastName":"豊"}""")
            .assertThat()
            .hasStatus(HttpStatus.CONFLICT)
            .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
            .bodyJson()
            .extractingPath("$.errorCode")
            .isEqualTo("duplicate-jockey")
    }

    @Test
    fun `想定外の例外発生時に 500 と problem+json が返ること`() {
        every { registerJockey(any<Command<RegisterJockeyCommand>>()) } throws
            RuntimeException("予期しない障害")

        tester
            .post()
            .uri("/api/jockeys")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"firstName":"武","lastName":"豊"}""")
            .assertThat()
            .hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
            .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
            .bodyJson()
            .extractingPath("$.errorCode")
            .isEqualTo("internal-server-error")
    }
}
