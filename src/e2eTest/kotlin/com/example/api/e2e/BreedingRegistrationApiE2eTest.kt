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
 * 繁殖登録 API のブラックボックス E2E。
 *
 * アプリを実 port で起動し（@SpringBootTest RANDOM_PORT）、Testcontainers PostgreSQL を
 * [PostgresContainerSupport] 経由で実配線したまま、[RestTestClient] で HTTP 越しに叩く （`BloodHorseApiE2eTest` と同型）。
 *
 * `/api` 配下は認証必須（ADR-0064）なので、各リクエストに Bearer トークンを載せる。`JwtDecoder` は [TestJwtDecoderConfiguration]
 * の HS256 実装に差し替え、実 JWKS を引かずに認証フィルタを本物のまま通す。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestJwtDecoderConfiguration::class)
@TestConstructor(autowireMode = AutowireMode.ALL)
class BreedingRegistrationApiE2eTest(val restTestClient: RestTestClient) :
    PostgresContainerSupport() {

    private lateinit var worldId: String

    /**
     * ドメイン API は `/api/worlds/{worldId}/...` に居るため、各テストの前にアカウントと世界を用意して そのIDを控える（#705）。基底クラスの
     * TRUNCATE（@BeforeEach）は JUnit がスーパークラス側を先に走らせるため、 truncate → provision の順になる。
     */
    @BeforeEach
    fun provisionWorld() {
        worldId = restTestClient.provisionAndFirstWorldId()
    }

    @Test
    fun `存在しない ID の照会は 404 と RFC9457 problem+json を返す`() {
        val missingId = "00000000-0000-0000-0000-000000000000"
        restTestClient
            .get()
            .uri("/api/worlds/{worldId}/breedingRegistrations/{id}", worldId, missingId)
            .header(HttpHeaders.AUTHORIZATION, TestJwt.bearerToken())
            .exchange()
            .expectStatus()
            .isNotFound
            .expectHeader()
            .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.error_code")
            .isEqualTo("registration-not-found")
            .jsonPath("$.status")
            .isEqualTo(404)
            .jsonPath("$.breeding_registration_id")
            .isEqualTo(missingId)
    }

    @Test
    fun `成立させた繁殖登録を ID で照会できる（write→read 往復）`() {
        // 繁殖登録の対象となる個体を血統登録する（父母不明の輸入馬が最短経路）。
        val horseId =
            restTestClient.registerImportedHorse(
                worldId = worldId,
                sex = "FEMALE",
                registrationNumber = "E2E-BR-MARE-001",
                microchipNumber = "392140000020001",
            )

        // 繁殖登録を成立させる（書き込み）。実 DB へ INSERT され、201 でリソース表現が返る。
        val createdBody =
            restTestClient
                .post()
                .uri("/api/worlds/{worldId}/breedingRegistrations", worldId)
                .header(HttpHeaders.AUTHORIZATION, TestJwt.bearerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(mapOf("blood_horse_id" to horseId, "registration_number" to "E2E-BR-0001"))
                .exchange()
                .expectStatus()
                .isCreated
                .expectBody()
                .returnResult()
                .responseBody
        val registrationId = JsonPath.read<String>(String(createdBody!!), "$.id")

        // 照会（実 DB から別リクエストで読み戻す）。
        restTestClient
            .get()
            .uri("/api/worlds/{worldId}/breedingRegistrations/{id}", worldId, registrationId)
            .header(HttpHeaders.AUTHORIZATION, TestJwt.bearerToken())
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.id")
            .isEqualTo(registrationId)
            .jsonPath("$.registration_number")
            .isEqualTo("E2E-BR-0001")
            .jsonPath("$.registered_horse_id")
            .isEqualTo(horseId)
            // ロールは対象個体の性から定まる（雌 → 繁殖牝馬）。wire enum で公開される。
            .jsonPath("$.role")
            .isEqualTo("BROODMARE")
            // 成立直後は供用中。共在 2 列の在不在が null として往復すること。
            .jsonPath("$.retirement")
            .isEmpty
    }

    @Test
    fun `供用停止した繁殖登録は照会で供用停止が見え、重ねての供用停止は 409 になる`() {
        val horseId =
            restTestClient.registerImportedHorse(
                worldId = worldId,
                sex = "MALE",
                registrationNumber = "E2E-BR-SIRE-001",
                microchipNumber = "392140000020002",
            )
        val createdBody =
            restTestClient
                .post()
                .uri("/api/worlds/{worldId}/breedingRegistrations", worldId)
                .header(HttpHeaders.AUTHORIZATION, TestJwt.bearerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(mapOf("blood_horse_id" to horseId, "registration_number" to "E2E-BR-0002"))
                .exchange()
                .expectStatus()
                .isCreated
                .expectBody()
                .returnResult()
                .responseBody
        val registrationId = JsonPath.read<String>(String(createdBody!!), "$.id")
        val retireUri = "/api/worlds/{worldId}/breedingRegistrations/{id}:retire"
        val retireBody = mapOf("reason" to "USE_CHANGE", "occurred_on" to "2025-03-01")

        // 供用停止（繁殖登録にとって初めての update 経路。楽観ロック付きで実 DB を更新する）。
        restTestClient
            .post()
            .uri(retireUri, worldId, registrationId)
            .header(HttpHeaders.AUTHORIZATION, TestJwt.bearerToken())
            .contentType(MediaType.APPLICATION_JSON)
            .body(retireBody)
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.retirement.reason")
            .isEqualTo("USE_CHANGE")

        // 照会（読み取り経路）にも供用停止が反映されていること。
        restTestClient
            .get()
            .uri("/api/worlds/{worldId}/breedingRegistrations/{id}", worldId, registrationId)
            .header(HttpHeaders.AUTHORIZATION, TestJwt.bearerToken())
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.retirement.reason")
            .isEqualTo("USE_CHANGE")
            .jsonPath("$.retirement.occurred_on")
            .isEqualTo("2025-03-01")

        // 供用停止は一度だけ。再停止は状態の競合として 409。
        restTestClient
            .post()
            .uri(retireUri, worldId, registrationId)
            .header(HttpHeaders.AUTHORIZATION, TestJwt.bearerToken())
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("reason" to "DEATH", "occurred_on" to "2025-04-01"))
            .exchange()
            .expectStatus()
            .isEqualTo(409)
            .expectBody()
            .jsonPath("$.error_code")
            .isEqualTo("breeding-registration-already-retired")
            .jsonPath("$.current_reason")
            .isEqualTo("USE_CHANGE")
    }
}
