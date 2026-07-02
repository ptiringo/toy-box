package com.example.api.actuator

import com.example.api.support.PostgresContainerSupport
import org.junit.jupiter.api.Test
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestConstructor
import org.springframework.test.context.TestConstructor.AutowireMode
import org.springframework.test.web.servlet.client.RestTestClient

// datasource は外部供給（H2 全面脱却・#451）のため、コンテキスト起動には
// PostgresContainerSupport の Testcontainers PostgreSQL を注入する。
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@TestConstructor(autowireMode = AutowireMode.ALL)
class HealthEndpointTest(val restTestClient: RestTestClient) : PostgresContainerSupport() {
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
