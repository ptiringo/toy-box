package com.example.api.e2e

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
 * 世界 API のブラックボックス E2E。
 *
 * 認可がテナント分離に一本化されたことを、実配線のまま HTTP 越しに確かめる。とくに「他人の世界が
 * 見えない・触れない」は漏れても例外が出ず静かにデータが混ざる種類の欠陥なので、ここで実測する。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestJwtDecoderConfiguration::class)
@TestConstructor(autowireMode = AutowireMode.ALL)
class WorldApiE2eTest(val restTestClient: RestTestClient) : PostgresContainerSupport() {

    /** 初回セットアップを済ませ、そのプレイヤーの Bearer トークンを返す。 */
    private fun provision(subject: String): String {
        val token = TestJwt.bearerToken(subject)
        restTestClient
            .post()
            .uri("/api/me:provision")
            .header(HttpHeaders.AUTHORIZATION, token)
            .exchange()
            .expectStatus()
            .isOk
        return token
    }

    private fun createWorld(token: String, name: String): String {
        val body =
            restTestClient
                .post()
                .uri("/api/worlds")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(mapOf("name" to name))
                .exchange()
                .expectStatus()
                .isCreated
                .expectBody()
                .returnResult()
                .responseBody
        return JsonPath.read<String>(String(body!!), "$.id")
    }

    @Test
    fun `初回セットアップは冪等で 2 回叩いても世界は増えない`() {
        val token = provision("sub-idempotent")

        // 2 回目。アカウントも世界も作り直されない。
        restTestClient
            .post()
            .uri("/api/me:provision")
            .header(HttpHeaders.AUTHORIZATION, token)
            .exchange()
            .expectStatus()
            .isOk

        restTestClient
            .get()
            .uri("/api/worlds")
            .header(HttpHeaders.AUTHORIZATION, token)
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.length()")
            .isEqualTo(1)
    }

    @Test
    fun `セットアップ前に叩くと 403 account-not-provisioned を返す`() {
        restTestClient
            .get()
            .uri("/api/worlds")
            .header(HttpHeaders.AUTHORIZATION, TestJwt.bearerToken("sub-not-provisioned"))
            .exchange()
            .expectStatus()
            .isForbidden
            .expectHeader()
            .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.error_code")
            .isEqualTo("account-not-provisioned")
    }

    @Test
    fun `作った世界は自分の一覧にだけ現れる`() {
        val alice = provision("sub-alice")
        val bob = provision("sub-bob")
        createWorld(alice, "アリスの牧場")

        // アリスの一覧には「はじまりの世界」＋作った「アリスの牧場」の 2 件が現れる。
        restTestClient
            .get()
            .uri("/api/worlds")
            .header(HttpHeaders.AUTHORIZATION, alice)
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.length()")
            .isEqualTo(2)
            .jsonPath("$[?(@.name == 'アリスの牧場')]")
            .isNotEmpty

        // ボブの一覧には初回セットアップで生えた世界しか無い。
        restTestClient
            .get()
            .uri("/api/worlds")
            .header(HttpHeaders.AUTHORIZATION, bob)
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.length()")
            .isEqualTo(1)
            .jsonPath("$[?(@.name == 'アリスの牧場')]")
            .isEmpty
    }

    @Test
    fun `他人の世界は改名できず 404 を返す`() {
        val alice = provision("sub-owner-rename")
        val bob = provision("sub-intruder-rename")
        val worldId = createWorld(alice, "アリスの牧場")

        restTestClient
            .patch()
            .uri("/api/worlds/{worldId}", worldId)
            .header(HttpHeaders.AUTHORIZATION, bob)
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("name" to "ボブが乗っ取った牧場"))
            .exchange()
            .expectStatus()
            .isNotFound
            .expectHeader()
            .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.error_code")
            .isEqualTo("world-not-found")

        // アリスから見れば名前も変わっていない（無傷のまま残っている）。
        restTestClient
            .get()
            .uri("/api/worlds")
            .header(HttpHeaders.AUTHORIZATION, alice)
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$[?(@.name == 'アリスの牧場')]")
            .isNotEmpty
            .jsonPath("$[?(@.name == 'ボブが乗っ取った牧場')]")
            .isEmpty
    }

    @Test
    fun `他人の世界は削除できず 404 を返す`() {
        val alice = provision("sub-owner-delete")
        val bob = provision("sub-intruder-delete")
        val worldId = createWorld(alice, "消されたくない牧場")

        restTestClient
            .delete()
            .uri("/api/worlds/{worldId}", worldId)
            .header(HttpHeaders.AUTHORIZATION, bob)
            .exchange()
            .expectStatus()
            .isNotFound
            .expectHeader()
            .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.error_code")
            .isEqualTo("world-not-found")

        // アリスから見れば無傷のまま残っている。
        restTestClient
            .get()
            .uri("/api/worlds")
            .header(HttpHeaders.AUTHORIZATION, alice)
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$[?(@.name == '消されたくない牧場')]")
            .isNotEmpty
    }

    @Test
    fun `自分の世界は改名して削除できる`() {
        val token = provision("sub-own-lifecycle")
        val worldId = createWorld(token, "旧名の牧場")

        restTestClient
            .patch()
            .uri("/api/worlds/{worldId}", worldId)
            .header(HttpHeaders.AUTHORIZATION, token)
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("name" to "新名の牧場"))
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.name")
            .isEqualTo("新名の牧場")

        restTestClient
            .delete()
            .uri("/api/worlds/{worldId}", worldId)
            .header(HttpHeaders.AUTHORIZATION, token)
            .exchange()
            .expectStatus()
            .isNoContent

        restTestClient
            .get()
            .uri("/api/worlds")
            .header(HttpHeaders.AUTHORIZATION, token)
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$[?(@.name == '新名の牧場')]")
            .isEmpty
    }
}
