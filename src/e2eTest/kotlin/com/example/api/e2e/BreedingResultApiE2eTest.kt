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
 * 繁殖成績 API のブラックボックス E2E。
 *
 * アプリを実 port で起動し（@SpringBootTest RANDOM_PORT）、Testcontainers PostgreSQL を
 * [PostgresContainerSupport] 経由で実配線したまま、[RestTestClient] で HTTP 越しに叩く （`BloodHorseApiE2eTest` と同型）。
 *
 * 繁殖成績は Create（種付記録）のあと `:reportFoaling`（分娩結果報告）・`:submitReport`（繁殖成績報告提出）で 状態が進む。往復シナリオはこの 3
 * 段すべてを踏んでから照会し、報告・提出まで進めないと埋まらない列 （分娩結果の判別子と分娩日・提出日・期限超過の導出）が実 DB 往復で壊れないことまで見る。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestJwtDecoderConfiguration::class)
@TestConstructor(autowireMode = AutowireMode.ALL)
class BreedingResultApiE2eTest(val restTestClient: RestTestClient) : PostgresContainerSupport() {

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
            .uri("/api/worlds/{worldId}/breedingResults/{id}", worldId, missingId)
            .header(HttpHeaders.AUTHORIZATION, TestJwt.bearerToken())
            .exchange()
            .expectStatus()
            .isNotFound
            .expectHeader()
            .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.error_code")
            .isEqualTo("breeding-result-not-found")
            .jsonPath("$.status")
            .isEqualTo(404)
            .jsonPath("$.breeding_result_id")
            .isEqualTo(missingId)
    }

    @Test
    fun `種付から分娩結果報告・提出まで進めた繁殖成績を ID で照会できる（write→read 往復）`() {
        val broodmareRegistrationId = seedRegistration(sex = "FEMALE", suffix = "MARE")
        val stallionRegistrationId = seedRegistration(sex = "MALE", suffix = "STALLION")

        val breedingResultId = recordCovering(broodmareRegistrationId, stallionRegistrationId)
        reportFoaling(breedingResultId)
        submitReport(breedingResultId)

        // 照会（実 DB から別リクエストで読み戻す）。3 段の状態遷移が反映された完全表現が返ること。
        restTestClient
            .get()
            .uri("/api/worlds/{worldId}/breedingResults/{id}", worldId, breedingResultId)
            .header(HttpHeaders.AUTHORIZATION, TestJwt.bearerToken())
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.id")
            .isEqualTo(breedingResultId)
            .jsonPath("$.breeding_registration_id")
            .isEqualTo(broodmareRegistrationId)
            // 繁殖年は種付日から導出される。
            .jsonPath("$.breeding_year")
            .isEqualTo(2024)
            // フラット化した種付列が往復すること。
            .jsonPath("$.covering_date")
            .isEqualTo("2024-04-01")
            .jsonPath("$.covering_place")
            .isEqualTo("北海道")
            .jsonPath("$.certificate_number")
            .isEqualTo("E2E-C-2024-0001")
            // 分娩結果は判別子＋分娩日にフラット化して保存され、読み取りで sealed に復元される。
            .jsonPath("$.outcome.kind")
            .isEqualTo("LIVE_FOAL")
            .jsonPath("$.outcome.foaling_date")
            .isEqualTo("2025-03-20")
            // 提出日はサーバー時刻（JST の暦日）。列として保存される。
            .jsonPath("$.report_submitted_on")
            .isNotEmpty
            // 期限超過は列を持たない導出値。繁殖年 2024 の期限は 2025-05-31 で既に過ぎているため、
            // いつ実行しても超過側に落ちる（実行日に依存しない）。
            .jsonPath("$.report_submitted_late")
            .isEqualTo(true)
    }

    /** 繁殖登録を成立させて ID を返す（種付の当事者はいずれも繁殖登録済みであることが前提）。 */
    private fun seedRegistration(sex: String, suffix: String): String =
        restTestClient.registerBreedingRegistration(
            worldId = worldId,
            sex = sex,
            registrationNumber = "E2E-BRES-$suffix-001",
            microchipNumber = if (sex == "FEMALE") "392140000030001" else "392140000030002",
            breedingRegistrationNumber = "E2E-BRES-B-$suffix",
        )

    /** 種付を記録して繁殖成績の ID を返す。種畜証明書は種付日・種付場所を覆う内容にする。 */
    private fun recordCovering(
        broodmareRegistrationId: String,
        stallionRegistrationId: String,
    ): String {
        val body =
            restTestClient
                .post()
                .uri("/api/worlds/{worldId}/breedingResults", worldId)
                .header(HttpHeaders.AUTHORIZATION, TestJwt.bearerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                    mapOf(
                        "breeding_registration_id" to broodmareRegistrationId,
                        "covering" to
                            mapOf(
                                "stallion_registration_id" to stallionRegistrationId,
                                "covering_date" to "2024-04-01",
                                "covering_place" to "北海道",
                                "certificate_number" to "E2E-C-2024-0001",
                                "stud_certificate" to
                                    mapOf(
                                        "number" to "E2E-S-2024-0001",
                                        "valid_regions" to listOf("北海道"),
                                        "valid_period_start" to "2024-01-01",
                                        "valid_period_end" to "2024-12-31",
                                    ),
                            ),
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

    /** 分娩結果（生産）を報告する。提出は分娩結果の確定を前提とするため、提出より先に踏む必要がある。 */
    private fun reportFoaling(breedingResultId: String) {
        restTestClient
            .post()
            .uri(
                "/api/worlds/{worldId}/breedingResults/{id}:reportFoaling",
                worldId,
                breedingResultId,
            )
            .header(HttpHeaders.AUTHORIZATION, TestJwt.bearerToken())
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("outcome" to "LIVE_FOAL", "foaling_date" to "2025-03-20"))
            .exchange()
            .expectStatus()
            .isOk
    }

    /** 繁殖成績報告（様式第14号）を提出する。期限超過でも受理される。 */
    private fun submitReport(breedingResultId: String) {
        restTestClient
            .post()
            .uri(
                "/api/worlds/{worldId}/breedingResults/{id}:submitReport",
                worldId,
                breedingResultId,
            )
            .header(HttpHeaders.AUTHORIZATION, TestJwt.bearerToken())
            .exchange()
            .expectStatus()
            .isOk
    }
}
