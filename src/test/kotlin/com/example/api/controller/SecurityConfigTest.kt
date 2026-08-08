package com.example.api.controller

import com.example.api.support.PostgresContainerSupport
import com.example.api.support.TestJwt
import com.example.api.support.TestJwtDecoderConfiguration
import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.Test
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.TestConstructor
import org.springframework.test.context.TestConstructor.AutowireMode
import org.springframework.test.web.servlet.client.RestTestClient

/**
 * OAuth2 リソースサーバとしての認証フィルタチェーン（[SecurityConfig]）の検証。
 *
 * `@WebMvcTest` の slice は認証フィルタを無効化してあるため（ADR-0064）、フィルタ層の振る舞いはここと E2E だけが担保する。 `JwtDecoder` は
 * [TestJwtDecoderConfiguration] の HS256 実装に差し替え、実 JWKS を引かずに本物のフィルタチェーンを走らせる。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestJwtDecoderConfiguration::class)
@TestConstructor(autowireMode = AutowireMode.ALL)
class SecurityConfigTest(val restTestClient: RestTestClient) : PostgresContainerSupport() {

    // 認証フィルタは認可判定にルートの実在を要求しない（`anyRequest.authenticated()`）ため、
    // 未認証系の 2 件は実在しないパスのままでよい。
    private val anyJockeyUri = "/api/jockeys/00000000-0000-0000-0000-000000000000"
    private val missingJockeyId = "00000000-0000-0000-0000-000000000000"

    @Test
    fun `トークン無しの保護エンドポイントは 401 と RFC9457 problem+json を返す`() {
        restTestClient
            .get()
            .uri(anyJockeyUri)
            .exchange()
            .expectStatus()
            .isUnauthorized
            .expectHeader()
            .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectHeader()
            .valueEquals(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
            .expectBody()
            .jsonPath("$.type")
            .isEqualTo("urn:problem-type:unauthenticated")
            .jsonPath("$.title")
            .isEqualTo("Unauthenticated")
            .jsonPath("$.status")
            .isEqualTo(401)
            .jsonPath("$.error_code")
            .isEqualTo("unauthenticated")
    }

    @Test
    fun `署名が検証できないトークンは 401 になる`() {
        restTestClient
            .get()
            .uri(anyJockeyUri)
            .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-valid-jwt")
            .exchange()
            .expectStatus()
            .isUnauthorized
            .expectHeader()
            .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.error_code")
            .isEqualTo("unauthenticated")
    }

    @Test
    fun `有効なトークンは認証を通過しアプリケーションまで届く（不在の馬なので 404）`() {
        // 世界スコープ化（#704）以降、ドメイン API は Actor（アカウント ＋ 世界）を要求するため、
        // 先にブートストラップしないと認証は通っても 403 account-not-provisioned で止まる。
        // ここで観測したいのは「フィルタチェーンを通過したか」なので、その手前の条件を整える。
        restTestClient
            .post()
            .uri("/api/me:provision")
            .header(HttpHeaders.AUTHORIZATION, TestJwt.bearerToken())
            .exchange()
            .expectStatus()
            .is2xxSuccessful

        // ドメイン API（jockeys 含む）は #705 で /api/worlds/{worldId}/... 配下へ移ったため、
        // :provision が作った「はじまりの世界」の ID を先に引いてパスに載せる。
        val worldsBody =
            restTestClient
                .get()
                .uri("/api/worlds")
                .header(HttpHeaders.AUTHORIZATION, TestJwt.bearerToken())
                .exchange()
                .expectStatus()
                .isOk
                .expectBody()
                .returnResult()
                .responseBody
        val worldId = JsonPath.read<String>(String(worldsBody!!), "$[0].id")

        // 401（認証で弾かれた）ではなく 404（アプリのユースケースが応答した）であることが、
        // フィルタチェーンを通過した唯一の観測可能な証拠になる。
        restTestClient
            .get()
            .uri("/api/worlds/{worldId}/jockeys/{id}", worldId, missingJockeyId)
            .header(HttpHeaders.AUTHORIZATION, TestJwt.bearerToken())
            .exchange()
            .expectStatus()
            .isNotFound
            .expectBody()
            .jsonPath("$.type")
            .isEqualTo("urn:problem-type:jockey-not-found")
    }

    @Test
    fun `ヘルスチェックは認証なしで 200 を返す`() {
        restTestClient.get().uri("/actuator/health").exchange().expectStatus().isOk
    }

    @Test
    fun `OpenAPI ドキュメントは認証なしで 200 を返す`() {
        // 認証を掛けると generateOpenApiDocs（forked bootRun 経由）が失敗し OpenAPI lint の CI ゲートが壊れる。
        restTestClient.get().uri("/v3/api-docs").exchange().expectStatus().isOk
    }
}
