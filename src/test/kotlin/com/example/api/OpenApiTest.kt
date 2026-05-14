package com.example.api

import org.junit.jupiter.api.Test
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestConstructor
import org.springframework.test.context.TestConstructor.AutowireMode
import org.springframework.test.web.servlet.client.RestTestClient

/**
 * OpenAPI ドキュメント生成機能のテストクラス
 *
 * springdoc-openapi による REST API ドキュメントの自動生成が正しく動作することを検証します。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@TestConstructor(autowireMode = AutowireMode.ALL)
class OpenApiTest(private val restTestClient: RestTestClient) {
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
