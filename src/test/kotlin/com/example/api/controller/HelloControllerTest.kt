package com.example.api.controller

import com.example.api.application.iam.world.WorldQueries
import com.example.api.domain.iam.model.account.AccountRepository
import com.ninjasquad.springmockk.MockkBean
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.test.context.TestConstructor
import org.springframework.test.context.TestConstructor.AutowireMode
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.assertj.MockMvcTester

@Execution(ExecutionMode.SAME_THREAD)
@WebMvcTest(HelloController::class)
@AutoConfigureMockMvc(addFilters = false)
@TestConstructor(autowireMode = AutowireMode.ALL)
class HelloControllerTest(val mockMvc: MockMvc) {
    // WebMvcConfig（CurrentAccountArgumentResolver）が全 @WebMvcTest スライスへ自動で載るため必要（本テストの検証対象ではない）。
    @MockkBean private lateinit var accounts: AccountRepository
    @MockkBean private lateinit var worldQueries: WorldQueries

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
