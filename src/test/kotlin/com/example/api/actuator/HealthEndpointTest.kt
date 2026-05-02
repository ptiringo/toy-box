package com.example.api.actuator

import org.junit.jupiter.api.Test
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestConstructor
import org.springframework.test.context.TestConstructor.AutowireMode
import org.springframework.test.web.servlet.client.RestTestClient

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@TestConstructor(autowireMode = AutowireMode.ALL)
class HealthEndpointTest(
    val restTestClient: RestTestClient,
) {
    @Test
    fun `ヘルスエンドポイントを呼び出すとUPステータスが返される`() {
        restTestClient
            .get()
            .uri("/actuator/health")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.status")
            .isEqualTo("UP")
    }
}
