package com.example.api

import com.example.api.support.PostgresContainerSupport
import com.example.api.support.TestJwtDecoderConfiguration
import org.junit.jupiter.api.Test
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestConstructor
import org.springframework.test.context.TestConstructor.AutowireMode
import org.springframework.test.web.servlet.client.RestTestClient

/**
 * OpenAPI ドキュメント生成機能のテストクラス
 *
 * springdoc-openapi による REST API ドキュメントの自動生成が正しく動作することを検証します。 datasource は外部供給（H2 全面脱却・#451）のため
 * PostgresContainerSupport を継承する。
 *
 * `@Import` する [TestJwtDecoderConfiguration] は、web 環境を要する `@SpringBootTest` 5 クラス
 * （[com.example.api.ApiApplicationTests] / [com.example.api.mcp.McpDisabledByDefaultTest] /
 * [com.example.api.actuator.HealthEndpointTest] / [com.example.api.controller.SecurityConfigTest] /
 * 本クラス）でキーを揃えてコンテキストを 1 つ共有するためのもの（#817 / ADR-0077）。本テストは認証を 要しない `/v3/api-docs` しか叩かないので
 * `JwtDecoder` の実装は結果に影響しない。1 つでも構成がずれると そのクラスだけ別コンテキストへ分岐する。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestJwtDecoderConfiguration::class)
@TestConstructor(autowireMode = AutowireMode.ALL)
class OpenApiTest(private val restTestClient: RestTestClient) : PostgresContainerSupport() {
    @Test
    fun `OpenAPI の JSON ドキュメントが取得できること`() {
        restTestClient
            .get()
            .uri("/v3/api-docs")
            .exchange()
            .expectStatus()
            .isOk
            .expectHeader()
            .contentType("application/json")
            .expectBody()
            .jsonPath("$.openapi")
            .isEqualTo("3.1.0")
            .jsonPath("$.info.title")
            .isEqualTo("toy-box")
    }

    @Test
    fun `Swagger UI が表示されること`() {
        restTestClient
            .get()
            .uri("/swagger-ui/index.html")
            .exchange()
            .expectStatus()
            .isOk
            .expectHeader()
            .contentType("text/html")
            .expectBody(String::class.java)
            .value { body ->
                assert(body?.contains("Swagger UI") == true) { "Swagger UI のタイトルが含まれていません" }
            }
    }
}
