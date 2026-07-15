package com.example.api.controller

import com.example.api.application.iam.account.ResolveActorUseCase
import com.ninjasquad.springmockk.MockkBean
import org.junit.jupiter.api.Test
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.test.context.TestConstructor
import org.springframework.test.context.TestConstructor.AutowireMode
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.assertj.MockMvcTester

/**
 * `@WebMvcTest` は対象 Controller に関係なく `HandlerMethodArgumentResolver` 型の Bean （`WebMvcConfig` 経由の
 * `ActorArgumentResolver`）も自動検出するため、[ResolveActorUseCase] が 無いと ApplicationContext の起動に失敗する。全 slice
 * に共通のモック配線。
 */
@WebMvcTest(HelloController::class)
@AutoConfigureMockMvc(addFilters = false)
@TestConstructor(autowireMode = AutowireMode.ALL)
class HelloControllerTest(val mockMvc: MockMvc) {
    @MockkBean private lateinit var resolveActor: ResolveActorUseCase

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
