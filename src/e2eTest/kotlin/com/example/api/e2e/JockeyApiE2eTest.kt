package com.example.api.e2e

import com.example.api.support.PostgresContainerSupport
import com.example.api.support.TestJwt
import com.example.api.support.TestJwtDecoderConfiguration
import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.BeforeEach
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
 * ジョッキー API のブラックボックス E2E。
 *
 * アプリを実 port で起動し（@SpringBootTest RANDOM_PORT）、Testcontainers PostgreSQL を
 * [PostgresContainerSupport] 経由で実配線したまま、[RestTestClient] で HTTP 越しに叩く。 controller → application →
 * infrastructure → 実 DB の結線を本物で検証する。
 *
 * `/api` 配下は認証必須（ADR-0064）なので、各リクエストに Bearer トークンを載せる。`JwtDecoder` は [TestJwtDecoderConfiguration]
 * の HS256 実装に差し替え、実 JWKS を引かずに認証フィルタを本物のまま通す。
 *
 * Karate（`.feature` ＋ Runner）から素の Spring ネイティブ（RestTestClient）へ載せ替えた（ADR-0056）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestJwtDecoderConfiguration::class)
@TestConstructor(autowireMode = AutowireMode.ALL)
class JockeyApiE2eTest(val restTestClient: RestTestClient) : PostgresContainerSupport() {

    private lateinit var worldId: String

    /**
     * ドメイン API は `/api/worlds/{worldId}/...` に居るため、各テストの前にアカウントと世界を用意して そのIDを控える（#705）。基底クラスの
     * TRUNCATE（@BeforeEach）は JUnit がスーパークラス側を先に走らせるため、 truncate → provision の順になる。
     */
    @BeforeEach
    fun provisionWorld() {
        worldId = restTestClient.provisionAndFirstWorldId()
    }

    /** 冪等キー付きで 1 件登録し、返ってきたジョッキーIDを取り出す。 */
    private fun registerTakeWithKey(idempotencyKey: String): String {
        val body =
            restTestClient
                .post()
                .uri("/api/worlds/{worldId}/jockeys", worldId)
                .header(HttpHeaders.AUTHORIZATION, TestJwt.bearerToken())
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(mapOf("first_name" to "Yutaka", "last_name" to "Take"))
                .exchange()
                .expectStatus()
                .isCreated
                .expectBody()
                .returnResult()
                .responseBody
        return JsonPath.read<String>(String(body!!), "$.id")
    }

    @Test
    fun `同じ Idempotency-Key の再送は同じジョッキーを返す（二重登録されない）`() {
        val key = "e2e-idempotency-key"

        val first = registerTakeWithKey(key)
        val second = registerTakeWithKey(key)

        // 同じ ID が返る = 2 回目は再生であって新規作成ではない。
        assert(first == second)
    }

    @Test
    fun `存在しない ID の照会は 404 と RFC9457 problem+json を返す`() {
        val missingId = "00000000-0000-0000-0000-000000000000"
        restTestClient
            .get()
            .uri("/api/worlds/{worldId}/jockeys/{id}", worldId, missingId)
            .header(HttpHeaders.AUTHORIZATION, TestJwt.bearerToken())
            .exchange()
            .expectStatus()
            .isNotFound
            .expectHeader()
            .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.type")
            .isEqualTo("urn:problem-type:jockey-not-found")
            .jsonPath("$.title")
            .isEqualTo("Jockey not found")
            .jsonPath("$.status")
            .isEqualTo(404)
            .jsonPath("$.detail")
            .isEqualTo("指定された ID のジョッキーは存在しません。")
            .jsonPath("$.jockey_id")
            .isEqualTo(missingId)
    }

    @Test
    fun `登録したジョッキーを ID で照会できる（write→read 往復）`() {
        // 登録（書き込み）。実 DB へ INSERT され、201 でリソース表現が返る。
        val createdBody =
            restTestClient
                .post()
                .uri("/api/worlds/{worldId}/jockeys", worldId)
                .header(HttpHeaders.AUTHORIZATION, TestJwt.bearerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(mapOf("first_name" to "Yutaka", "last_name" to "Take"))
                .exchange()
                .expectStatus()
                .isCreated
                .expectBody()
                .jsonPath("$.first_name")
                .isEqualTo("Yutaka")
                .jsonPath("$.last_name")
                .isEqualTo("Take")
                .jsonPath("$.id")
                .isNotEmpty
                .returnResult()
                .responseBody
        val jockeyId = JsonPath.read<String>(String(createdBody!!), "$.id")

        // 照会（実 DB から別リクエストで読み戻す）。write→read の往復を本物の結線で検証する。
        restTestClient
            .get()
            .uri("/api/worlds/{worldId}/jockeys/{id}", worldId, jockeyId)
            .header(HttpHeaders.AUTHORIZATION, TestJwt.bearerToken())
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.id")
            .isEqualTo(jockeyId)
            .jsonPath("$.first_name")
            .isEqualTo("Yutaka")
            .jsonPath("$.last_name")
            .isEqualTo("Take")
    }
}
