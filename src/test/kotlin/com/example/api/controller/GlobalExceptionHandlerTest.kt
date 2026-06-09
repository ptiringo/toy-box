package com.example.api.controller

import com.example.api.application.horseracing.jockey.JockeyRegistrationUseCase
import com.example.api.application.horseracing.jockey.RegisterJockeyCommand
import com.example.api.controller.jockey.JockeyController
import com.example.api.domain.Command
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
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
@TestConstructor(autowireMode = AutowireMode.ALL)
class GlobalExceptionHandlerTest(val mockMvc: MockMvc) {
    @MockkBean private lateinit var registerJockey: JockeyRegistrationUseCase

    private val tester = MockMvcTester.create(mockMvc)

    @Test
    fun `必須フィールド欠落のリクエストボディで 400 と problem+json が返ること`() {
        // lastName が欠落しており Jackson のデシリアライズに失敗する
        tester
            .post()
            .uri("/api/jockeys")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"firstName":"武"}""")
            .assertThat()
            .hasStatus(HttpStatus.BAD_REQUEST)
            .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
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
