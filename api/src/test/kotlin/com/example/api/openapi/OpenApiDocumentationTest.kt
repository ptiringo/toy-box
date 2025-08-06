package com.example.api.openapi

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestConstructor
import org.springframework.test.context.TestConstructor.AutowireMode
import org.springframework.test.web.reactive.server.WebTestClient

/**
 * OpenAPI ドキュメント生成機能のテストクラス
 *
 * springdoc-openapi による REST API ドキュメントの自動生成が
 * 正しく動作することを検証します。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestConstructor(autowireMode = AutowireMode.ALL)
class OpenApiDocumentationTest(
    private val webTestClient: WebTestClient
) {

    @Test
    fun `should provide OpenAPI JSON documentation at v3 api-docs endpoint`() {
        webTestClient.get()
            .uri("/v3/api-docs")
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentType("application/json")
            .expectBody()
            .jsonPath("$.openapi").isEqualTo("3.0.1")
            .jsonPath("$.info.title").isEqualTo("OpenAPI definition")
            .jsonPath("$.paths['/api/hello']").exists()
            .jsonPath("$.paths['/api/hello'].get.summary").isEqualTo("Hello World エンドポイント")
            .jsonPath("$.paths['/api/hello'].get.description").isEqualTo("簡単な Hello World メッセージを返すエンドポイント")
            .jsonPath("$.paths['/api/hello'].get.tags[0]").isEqualTo("Hello")
            .jsonPath("$.paths['/api/hello'].get.operationId").isEqualTo("hello")
    }

    @Test
    fun `should redirect to Swagger UI when accessing swagger-ui html`() {
        webTestClient.get()
            .uri("/swagger-ui.html")
            .exchange()
            .expectStatus().is3xxRedirection()
    }

    @Test
    fun `should serve Swagger UI interface at redirected location`() {
        webTestClient.get()
            .uri("/webjars/swagger-ui/index.html")
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentType("text/html")
            .expectBody(String::class.java)
            .value { body ->
                assert(body.contains("Swagger UI")) { "Swagger UI のタイトルが含まれていません" }
                assert(body.contains("swagger-ui-bundle.js")) { "Swagger UI のJavaScriptファイルが参照されていません" }
            }
    }
}
