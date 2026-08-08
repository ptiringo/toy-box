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
 * 世界（テナント）の分離を実 HTTP で実測する E2E（#705 / ADR-0067）。
 *
 * 世界スコープの漏れは例外を出さず静かにデータが混ざる種類の欠陥で、slice テストは認証フィルタを無効化して いるため構造上捕まえられない。ここが唯一の backstop になる。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestJwtDecoderConfiguration::class)
@TestConstructor(autowireMode = AutowireMode.ALL)
class WorldScopeApiE2eTest(val restTestClient: RestTestClient) : PostgresContainerSupport() {

    @Test
    fun `世界 A で登録した馬は世界 B の一覧に出ない`() {
        val worldA = restTestClient.provisionAndFirstWorldId()
        val worldB = createSecondWorldViaApi("ふたつめの世界")
        registerImportedHorse(worldA, "E2E-SCOPE-A-001")

        // 世界 A には見える
        restTestClient
            .get()
            .uri("/api/worlds/{worldId}/bloodHorses", worldA)
            .header(HttpHeaders.AUTHORIZATION, TestJwt.bearerToken())
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.length()")
            .isEqualTo(1)

        // 同じアカウントの別の世界には見えない
        restTestClient
            .get()
            .uri("/api/worlds/{worldId}/bloodHorses", worldB)
            .header(HttpHeaders.AUTHORIZATION, TestJwt.bearerToken())
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.length()")
            .isEqualTo(0)
    }

    @Test
    fun `世界 A で登録した馬は世界 B の URL からは by-id でも引けず 404 horse-not-found を返す`() {
        val worldA = restTestClient.provisionAndFirstWorldId()
        val worldB = createSecondWorldViaApi("ふたつめの世界")
        val horseId = registerImportedHorse(worldA, "E2E-SCOPE-A-002")

        // 世界 B は自分の世界なので所有確認自体は通り、その世界に馬が無いという 404 になる
        // （world-not-found ではなく horse-not-found）。
        restTestClient
            .get()
            .uri("/api/worlds/{worldId}/bloodHorses/{id}", worldB, horseId)
            .header(HttpHeaders.AUTHORIZATION, TestJwt.bearerToken())
            .exchange()
            .expectStatus()
            .isNotFound
            .expectBody()
            .jsonPath("$.error_code")
            .isEqualTo("horse-not-found")
    }

    @Test
    fun `他人の世界の URL を叩くと 404 world-not-found を返す`() {
        restTestClient.provisionAndFirstWorldId()
        val othersWorld = restTestClient.provisionAndFirstWorldId("sub-other-player")

        restTestClient
            .get()
            .uri("/api/worlds/{worldId}/bloodHorses", othersWorld)
            .header(HttpHeaders.AUTHORIZATION, TestJwt.bearerToken())
            .exchange()
            .expectStatus()
            .isNotFound
            .expectHeader()
            .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.error_code")
            .isEqualTo("world-not-found")
    }

    @Test
    fun `存在しない世界の URL も他人の世界と同じ 404 を返す`() {
        restTestClient.provisionAndFirstWorldId()

        restTestClient
            .get()
            .uri("/api/worlds/{worldId}/bloodHorses", "00000000-0000-0000-0000-000000000000")
            .header(HttpHeaders.AUTHORIZATION, TestJwt.bearerToken())
            .exchange()
            .expectStatus()
            .isNotFound
            .expectBody()
            .jsonPath("$.error_code")
            .isEqualTo("world-not-found")
    }

    @Test
    fun `他人の世界へは書き込めない`() {
        restTestClient.provisionAndFirstWorldId()
        val othersWorld = restTestClient.provisionAndFirstWorldId("sub-other-writer")

        restTestClient
            .post()
            .uri("/api/worlds/{worldId}/jockeys", othersWorld)
            .header(HttpHeaders.AUTHORIZATION, TestJwt.bearerToken())
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("first_name" to "武", "last_name" to "豊"))
            .exchange()
            .expectStatus()
            .isNotFound
            .expectBody()
            .jsonPath("$.error_code")
            .isEqualTo("world-not-found")
    }

    /**
     * 自分のアカウントに 2 つめの世界を作り、そのIDを返す。
     *
     * [PostgresContainerSupport.createWorld] は「別のアカウント（他人）の世界」を DB へ直接作るための
     * ヘルパーで、ここで要るのは逆に「同じアカウント」の 2 つめの世界であり、かつ実際に `POST /api/worlds` を叩いた結果でなければならない（一覧漏れの検証対象そのものが実
     * HTTP 経路のため）。目的が違うため名前も分ける。
     */
    private fun createSecondWorldViaApi(name: String): String {
        val body =
            restTestClient
                .post()
                .uri("/api/worlds")
                .header(HttpHeaders.AUTHORIZATION, TestJwt.bearerToken())
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

    /** 指定した世界に父母不明の輸入馬を 1 頭登録し、その ID を返す（一覧に出る行を作るための最小の書き込み）。 */
    private fun registerImportedHorse(worldId: String, registrationNumber: String): String {
        val body =
            restTestClient
                .post()
                .uri("/api/worlds/{worldId}/bloodHorses:registerImported", worldId)
                .header(HttpHeaders.AUTHORIZATION, TestJwt.bearerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                    mapOf(
                        "sex" to "MALE",
                        "coat_color" to "BAY",
                        "breed_type" to "THOROUGHBRED",
                        "date_of_birth" to "2015-03-20",
                        "breeder" to "Coolmore",
                        "microchip_number" to "392140000020001",
                        "origin_country" to "アイルランド",
                        "landing_date" to "2020-09-01",
                        "registration_number" to registrationNumber,
                    )
                )
                .exchange()
                .expectStatus()
                .isCreated
                .expectBody()
                .returnResult()
                .responseBody
        return JsonPath.read<String>(String(body!!), "$.id")
    }
}
