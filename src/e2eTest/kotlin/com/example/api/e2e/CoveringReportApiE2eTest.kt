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
 * 種付成績報告 API のブラックボックス E2E。
 *
 * アプリを実 port で起動し（@SpringBootTest RANDOM_PORT）、Testcontainers PostgreSQL を
 * [PostgresContainerSupport] 経由で実配線したまま、[RestTestClient] で HTTP 越しに叩く （`BloodHorseApiE2eTest` と同型）。
 *
 * 雄側の年次報告は提出で初めてリソースが生まれる（insert-only）ため、往復は Create → Get の 2 段で足りる。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestJwtDecoderConfiguration::class)
@TestConstructor(autowireMode = AutowireMode.ALL)
class CoveringReportApiE2eTest(val restTestClient: RestTestClient) : PostgresContainerSupport() {

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
            .uri("/api/worlds/{worldId}/coveringReports/{id}", worldId, missingId)
            .header(HttpHeaders.AUTHORIZATION, TestJwt.bearerToken())
            .exchange()
            .expectStatus()
            .isNotFound
            .expectHeader()
            .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.error_code")
            .isEqualTo("covering-report-not-found")
            .jsonPath("$.status")
            .isEqualTo(404)
            .jsonPath("$.covering_report_id")
            .isEqualTo(missingId)
    }

    @Test
    fun `提出した種付成績報告を ID で照会できる（write→read 往復）`() {
        // 提出者は種牡馬の繁殖登録。ロールは対象個体の性から定まるため雄で登録する。
        val stallionRegistrationId =
            restTestClient.registerBreedingRegistration(
                worldId = worldId,
                sex = "MALE",
                registrationNumber = "E2E-CR-STALLION-001",
                microchipNumber = "392140000040001",
                breedingRegistrationNumber = "E2E-CR-B-0001",
            )

        // 種付成績報告を提出する（書き込み）。当年の種付実績が 0 件でも受理される（#540）。
        val createdBody =
            restTestClient
                .post()
                .uri("/api/worlds/{worldId}/coveringReports", worldId)
                .header(HttpHeaders.AUTHORIZATION, TestJwt.bearerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                    mapOf(
                        "stallion_breeding_registration_id" to stallionRegistrationId,
                        "covering_year" to 2024,
                    )
                )
                .exchange()
                .expectStatus()
                .isCreated
                .expectBody()
                .returnResult()
                .responseBody
        val reportId = JsonPath.read<String>(String(createdBody!!), "$.id")

        // 照会（実 DB から別リクエストで読み戻す）。
        restTestClient
            .get()
            .uri("/api/worlds/{worldId}/coveringReports/{id}", worldId, reportId)
            .header(HttpHeaders.AUTHORIZATION, TestJwt.bearerToken())
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.id")
            .isEqualTo(reportId)
            .jsonPath("$.stallion_breeding_registration_id")
            .isEqualTo(stallionRegistrationId)
            .jsonPath("$.covering_year")
            .isEqualTo(2024)
            // 提出日はサーバー時刻（JST の暦日）。列として保存される。
            .jsonPath("$.submitted_on")
            .isNotEmpty
            // 期限超過は列を持たない導出値。種付年 2024 の期限は当年 9/30 で既に過ぎているため、
            // いつ実行しても超過側に落ちる（実行日に依存しない）。
            .jsonPath("$.submitted_late")
            .isEqualTo(true)
    }
}
