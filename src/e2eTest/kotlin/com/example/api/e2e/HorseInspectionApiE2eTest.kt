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
 * 審査 API のブラックボックス E2E。
 *
 * アプリを実 port で起動し（@SpringBootTest RANDOM_PORT）、Testcontainers PostgreSQL を
 * [PostgresContainerSupport] 経由で実配線したまま、[RestTestClient] で HTTP 越しに叩く。 controller → application →
 * infrastructure → 実 DB の結線を本物で検証する（`JockeyApiE2eTest` と同型）。
 *
 * `/api` 配下は認証必須（ADR-0064）なので、各リクエストに Bearer トークンを載せる。`JwtDecoder` は [TestJwtDecoderConfiguration]
 * の HS256 実装に差し替え、実 JWKS を引かずに認証フィルタを本物のまま通す。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestJwtDecoderConfiguration::class)
@TestConstructor(autowireMode = AutowireMode.ALL)
class HorseInspectionApiE2eTest(val restTestClient: RestTestClient) : PostgresContainerSupport() {

    @Test
    fun `存在しない ID の照会は 404 と RFC9457 problem+json を返す`() {
        val missingId = "00000000-0000-0000-0000-000000000000"
        restTestClient
            .get()
            .uri("/api/horseInspections/{id}", missingId)
            .header(HttpHeaders.AUTHORIZATION, TestJwt.bearerToken())
            .exchange()
            .expectStatus()
            .isNotFound
            .expectHeader()
            .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.error_code")
            .isEqualTo("horse-inspection-not-found")
            .jsonPath("$.status")
            .isEqualTo(404)
            .jsonPath("$.inspection_id")
            .isEqualTo(missingId)
    }

    @Test
    fun `記録した審査を ID で照会できる（write→read 往復）`() {
        // 記録（書き込み）。実 DB へ INSERT され、201 でリソース表現が返る。
        val createdBody =
            restTestClient
                .post()
                .uri("/api/horseInspections")
                .header(HttpHeaders.AUTHORIZATION, TestJwt.bearerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                    mapOf(
                        "microchip_number" to "392140000000123",
                        "parentage" to
                            mapOf("type" to "BY_DNA", "dna_parentage_result" to "CONSISTENT"),
                        "features" to mapOf("hair_whorl" to "頭部正中", "white_markings" to "流星"),
                    )
                )
                .exchange()
                .expectStatus()
                .isCreated
                .expectBody()
                .jsonPath("$.microchip_number")
                .isEqualTo("392140000000123")
                .jsonPath("$.id")
                .isNotEmpty
                .returnResult()
                .responseBody
        val inspectionId = JsonPath.read<String>(String(createdBody!!), "$.id")

        // 照会（実 DB から別リクエストで読み戻す）。write→read の往復を本物の結線で検証する。
        restTestClient
            .get()
            .uri("/api/horseInspections/{id}", inspectionId)
            .header(HttpHeaders.AUTHORIZATION, TestJwt.bearerToken())
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.id")
            .isEqualTo(inspectionId)
            .jsonPath("$.microchip_number")
            .isEqualTo("392140000000123")
            .jsonPath("$.parentage.type")
            .isEqualTo("BY_DNA")
            .jsonPath("$.parentage.dna_parentage_result")
            .isEqualTo("CONSISTENT")
            .jsonPath("$.features.hair_whorl")
            .isEqualTo("頭部正中")
            .jsonPath("$.features.white_markings")
            .isEqualTo("流星")
    }
}
