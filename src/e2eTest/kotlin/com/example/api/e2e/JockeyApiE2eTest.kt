package com.example.api.e2e

import com.example.api.domain.iam.model.account.Account
import com.example.api.domain.iam.model.account.AccountRepository
import com.example.api.domain.iam.model.account.Role
import com.example.api.domain.iam.model.account.SubjectId
import com.example.api.support.PostgresContainerSupport
import com.example.api.support.TestJwt
import com.example.api.support.TestJwtDecoderConfiguration
import com.example.api.support.deleteAllIamTables
import com.example.api.support.deleteAllRacingTables
import com.github.michaelbull.result.unwrap
import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.simple.JdbcClient
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
 * の HS256 実装に差し替え、実 JWKS を引かずに認証フィルタを本物のまま通す。書き込み（登録）は認可（#606）も通るため、 トークンの subject に対応する REGISTRAR
 * アカウントを DB へ seed してから叩く（認可そのものの網羅は [AuthorizationE2eTest]）。
 *
 * Karate（`.feature` ＋ Runner）から素の Spring ネイティブ（RestTestClient）へ載せ替えた（ADR-0056）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestJwtDecoderConfiguration::class)
@TestConstructor(autowireMode = AutowireMode.ALL)
class JockeyApiE2eTest(
    val restTestClient: RestTestClient,
    val accountRepository: AccountRepository,
    val jdbcClient: JdbcClient,
) : PostgresContainerSupport() {

    @BeforeEach
    fun seedRegistrarAccount() {
        deleteAllRacingTables(jdbcClient)
        deleteAllIamTables(jdbcClient)
        // 既定 subject（test-user）を REGISTRAR として登録し、書き込みの認可を通す。
        accountRepository.save(
            Account.create(SubjectId("test-user"), setOf(Role.REGISTRAR)).unwrap()
        )
    }

    @AfterEach
    fun cleanUp() {
        deleteAllRacingTables(jdbcClient)
        deleteAllIamTables(jdbcClient)
    }

    @Test
    fun `存在しない ID の照会は 404 と RFC9457 problem+json を返す`() {
        val missingId = "00000000-0000-0000-0000-000000000000"
        restTestClient
            .get()
            .uri("/api/jockeys/{id}", missingId)
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
                .uri("/api/jockeys")
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
            .uri("/api/jockeys/{id}", jockeyId)
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
