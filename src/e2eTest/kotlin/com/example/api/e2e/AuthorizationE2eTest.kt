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
 * 認可（RBAC・#606 / ADR-0064）のブラックボックス E2E。
 *
 * 認証は本物のフィルタチェーン（[TestJwtDecoderConfiguration] の HS256 decoder）で通し、認可は application 層が 自前 DB
 * のロール・権限で判定する。実配線のまま HTTP 越しに、subject（＝ロール）を変えて次を実測する:
 * - A: REGISTRAR（`racing:jockey:register` を持つ）でジョッキー登録 → 201
 * - B: VIEWER（書き込み権限なし）で登録 → 403・`error_code=forbidden`
 * - C: account 未登録の subject で登録 → 403・`error_code=account-not-provisioned`
 * - D: VIEWER で照会（GET）→ 権限不要なので通る（200）
 *
 * とりわけ B / C の 403 が `application/problem+json` で返ることを実測して、**認可の 403 が `AccessDeniedHandler` 無しで中央の
 * MVC 例外 funnel を通って描画される**ことを確認する（SecurityConfig は フィルタ層で認可せず、403 経路をフィルタに持たない）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestJwtDecoderConfiguration::class)
@TestConstructor(autowireMode = AutowireMode.ALL)
class AuthorizationE2eTest(
    val restTestClient: RestTestClient,
    val accountRepository: AccountRepository,
    val jdbcClient: JdbcClient,
) : PostgresContainerSupport() {

    @BeforeEach
    fun seedAccounts() {
        deleteAllRacingTables(jdbcClient)
        deleteAllIamTables(jdbcClient)
        // REGISTRAR は全書き込み権限（racing:jockey:register を含む）、VIEWER は書き込み権限を持たない。
        // UNPROVISIONED_SUBJECT は account を作らない（認証は通るが何も許可されていない利用者）。
        accountRepository.save(
            Account.create(SubjectId(REGISTRAR_SUBJECT), setOf(Role.REGISTRAR)).unwrap()
        )
        accountRepository.save(
            Account.create(SubjectId(VIEWER_SUBJECT), setOf(Role.VIEWER)).unwrap()
        )
    }

    @AfterEach
    fun cleanUp() {
        deleteAllRacingTables(jdbcClient)
        deleteAllIamTables(jdbcClient)
    }

    @Test
    fun `A_ REGISTRAR はジョッキーを登録できる（201）`() {
        restTestClient
            .post()
            .uri("/api/jockeys")
            .header(HttpHeaders.AUTHORIZATION, TestJwt.bearerToken(REGISTRAR_SUBJECT))
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("first_name" to "Yutaka", "last_name" to "Take"))
            .exchange()
            .expectStatus()
            .isCreated
            .expectBody()
            .jsonPath("$.first_name")
            .isEqualTo("Yutaka")
            .jsonPath("$.id")
            .isNotEmpty
    }

    @Test
    fun `B_ VIEWER の登録は 403 forbidden の problem+json`() {
        restTestClient
            .post()
            .uri("/api/jockeys")
            .header(HttpHeaders.AUTHORIZATION, TestJwt.bearerToken(VIEWER_SUBJECT))
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("first_name" to "Yutaka", "last_name" to "Take"))
            .exchange()
            .expectStatus()
            .isForbidden
            .expectHeader()
            .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.status")
            .isEqualTo(403)
            .jsonPath("$.error_code")
            .isEqualTo("forbidden")
    }

    @Test
    fun `C_ account 未登録の subject の登録は 403 account-not-provisioned の problem+json`() {
        restTestClient
            .post()
            .uri("/api/jockeys")
            .header(HttpHeaders.AUTHORIZATION, TestJwt.bearerToken(UNPROVISIONED_SUBJECT))
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("first_name" to "Yutaka", "last_name" to "Take"))
            .exchange()
            .expectStatus()
            .isForbidden
            .expectHeader()
            .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.status")
            .isEqualTo(403)
            .jsonPath("$.error_code")
            .isEqualTo("account-not-provisioned")
    }

    @Test
    fun `D_ VIEWER でも照会（GET）は権限不要で通る（200）`() {
        // 事前に REGISTRAR で 1 頭登録し、その ID を VIEWER で読み戻す。
        val createdBody =
            restTestClient
                .post()
                .uri("/api/jockeys")
                .header(HttpHeaders.AUTHORIZATION, TestJwt.bearerToken(REGISTRAR_SUBJECT))
                .contentType(MediaType.APPLICATION_JSON)
                .body(mapOf("first_name" to "Yutaka", "last_name" to "Take"))
                .exchange()
                .expectStatus()
                .isCreated
                .expectBody()
                .returnResult()
                .responseBody
        val jockeyId = JsonPath.read<String>(String(createdBody!!), "$.id")

        restTestClient
            .get()
            .uri("/api/jockeys/{id}", jockeyId)
            .header(HttpHeaders.AUTHORIZATION, TestJwt.bearerToken(VIEWER_SUBJECT))
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.id")
            .isEqualTo(jockeyId)
    }

    private companion object {
        const val REGISTRAR_SUBJECT = "e2e-registrar"
        const val VIEWER_SUBJECT = "e2e-viewer"
        const val UNPROVISIONED_SUBJECT = "e2e-unprovisioned"
    }
}
