package com.example.api.actuator

import com.example.api.support.PostgresContainerSupport
import com.example.api.support.TestJwtDecoderConfiguration
import org.junit.jupiter.api.Test
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestConstructor
import org.springframework.test.context.TestConstructor.AutowireMode
import org.springframework.test.web.servlet.client.RestTestClient

// datasource は外部供給（H2 全面脱却・#451）のため、コンテキスト起動には
// PostgresContainerSupport の Testcontainers PostgreSQL を注入する。
//
// @Import(TestJwtDecoderConfiguration) は、web 環境を要する @SpringBootTest 5 クラス（ApiApplicationTests /
// McpDisabledByDefaultTest / HealthEndpointTest / OpenApiTest / SecurityConfigTest）でキーを揃えて
// コンテキストを 1 つ共有するためのもの（#817 / ADR-0077）。本テストは認証を要しないエンドポイントしか
// 叩かないので JwtDecoder の実装は結果に影響しない。1 つでも構成がずれると別コンテキストへ分岐する。
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestJwtDecoderConfiguration::class)
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
