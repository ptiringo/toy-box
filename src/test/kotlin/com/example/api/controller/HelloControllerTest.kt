package com.example.api.controller

import org.junit.jupiter.api.Test
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.test.context.TestConstructor
import org.springframework.test.context.TestConstructor.AutowireMode
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.assertj.MockMvcTester

@WebMvcTest(HelloController::class)
@TestConstructor(autowireMode = AutowireMode.ALL)
class HelloControllerTest(
    val mockMvc: MockMvc,
) {
    private val tester = MockMvcTester.create(mockMvc)

    @Test
    fun `helloエンドポイントがHello Worldを返すこと`() {
        tester
            .get()
            .uri("/api/hello")
            .assertThat()
            .hasStatusOk()
            .bodyJson()
            .extractingPath("$.message")
            .isEqualTo("Hello World")
    }
}
