package com.example.api.controller.jockey

import com.example.api.application.horseracing.jockey.JockeyRegistrationError
import com.example.api.application.horseracing.jockey.JockeyRegistrationUseCase
import com.example.api.application.horseracing.jockey.RegisterJockeyCommand
import com.example.api.domain.horseracing.model.jockey.Jockey
import com.example.api.domain.horseracing.model.jockey.JockeyValidationError
import com.example.api.domain.shared.Command
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.TestConstructor
import org.springframework.test.context.TestConstructor.AutowireMode
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.assertj.MockMvcTester

@WebMvcTest(JockeyController::class)
@TestConstructor(autowireMode = AutowireMode.ALL)
class JockeyControllerTest(val mockMvc: MockMvc) {
    @MockkBean private lateinit var registerJockey: JockeyRegistrationUseCase

    private val tester = MockMvcTester.create(mockMvc)

    @Nested
    inner class SuccessCase {
        @Test
        fun `正常な入力で 201 Created と登録結果が返ること`() {
            val savedJockey = Jockey.create("武", "豊").unwrap()
            every { registerJockey(any<Command<RegisterJockeyCommand>>()) } returns Ok(savedJockey)

            tester
                .post()
                .uri("/api/jockeys")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"firstName":"武","lastName":"豊"}""")
                .assertThat()
                .hasStatus(HttpStatus.CREATED)
                .bodyJson()
                .extractingPath("$.firstName")
                .isEqualTo("武")
        }
    }

    @Nested
    inner class FailureCase {
        @Test
        fun `InvalidJockey(BlankFirstName) で 400 と problem+json が返ること`() {
            every { registerJockey(any<Command<RegisterJockeyCommand>>()) } returns
                Err(JockeyRegistrationError.InvalidJockey(JockeyValidationError.BlankFirstName))

            tester
                .post()
                .uri("/api/jockeys")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"firstName":"","lastName":"豊"}""")
                .assertThat()
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.errorCode")
                .isEqualTo("blank-first-name")
        }

        @Test
        fun `InvalidJockey(BlankLastName) で 400 と problem+json が返ること`() {
            every { registerJockey(any<Command<RegisterJockeyCommand>>()) } returns
                Err(JockeyRegistrationError.InvalidJockey(JockeyValidationError.BlankLastName))

            tester
                .post()
                .uri("/api/jockeys")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"firstName":"武","lastName":""}""")
                .assertThat()
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.errorCode")
                .isEqualTo("blank-last-name")
        }

        @Test
        fun `DuplicateJockey で 409 と existingId 付きの problem+json が返ること`() {
            val existing = Jockey.create("武", "豊").unwrap()
            every { registerJockey(any<Command<RegisterJockeyCommand>>()) } returns
                Err(JockeyRegistrationError.DuplicateJockey(existing.id))

            tester
                .post()
                .uri("/api/jockeys")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"firstName":"武","lastName":"豊"}""")
                .assertThat()
                .hasStatus(HttpStatus.CONFLICT)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.existingId")
                .isEqualTo(existing.id.value.toString())
        }
    }
}
