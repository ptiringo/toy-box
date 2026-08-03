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
 * 軽種馬 API のブラックボックス E2E。
 *
 * アプリを実 port で起動し（@SpringBootTest RANDOM_PORT）、Testcontainers PostgreSQL を
 * [PostgresContainerSupport] 経由で実配線したまま、[RestTestClient] で HTTP 越しに叩く（`JockeyApiE2eTest` と同型）。
 *
 * `/api` 配下は認証必須（ADR-0064）なので、各リクエストに Bearer トークンを載せる。`JwtDecoder` は [TestJwtDecoderConfiguration]
 * の HS256 実装に差し替え、実 JWKS を引かずに認証フィルタを本物のまま通す。
 *
 * 内国産馬の血統登録は父母が登録済みであることを要求するため、往復シナリオでは父母不明の輸入馬を登録する カスタムメソッド `:registerImported` で父母を 2 頭 seed
 * してから産駒を血統登録する。父母も通常の血統登録で 作ろうとすると更にその父母が要る無限後退になるため、輸入馬を系統の起点に使う。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestJwtDecoderConfiguration::class)
@TestConstructor(autowireMode = AutowireMode.ALL)
class BloodHorseApiE2eTest(val restTestClient: RestTestClient) : PostgresContainerSupport() {

    @Test
    fun `存在しない ID の照会は 404 と RFC9457 problem+json を返す`() {
        val missingId = "00000000-0000-0000-0000-000000000000"
        restTestClient
            .get()
            .uri("/api/bloodHorses/{id}", missingId)
            .header(HttpHeaders.AUTHORIZATION, TestJwt.bearerToken())
            .exchange()
            .expectStatus()
            .isNotFound
            .expectHeader()
            .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.error_code")
            .isEqualTo("horse-not-found")
            .jsonPath("$.status")
            .isEqualTo(404)
            .jsonPath("$.blood_horse_id")
            .isEqualTo(missingId)
    }

    @Test
    fun `血統登録した軽種馬を ID で照会できる（write→read 往復）`() {
        // 父母を輸入馬として seed する（内国産の血統登録は父母の存在を前提とするため）。
        val sireId = registerImportedHorse(sex = "MALE", registrationNumber = "E2E-SIRE-001")
        val damId = registerImportedHorse(sex = "FEMALE", registrationNumber = "E2E-DAM-001")

        // 産駒を血統登録する（書き込み）。実 DB へ INSERT され、201 でリソース表現が返る。
        val createdBody =
            restTestClient
                .post()
                .uri("/api/bloodHorses")
                .header(HttpHeaders.AUTHORIZATION, TestJwt.bearerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                    mapOf(
                        "sire_id" to sireId,
                        "dam_id" to damId,
                        "sex" to "MALE",
                        "coat_color" to "BAY",
                        "breed_type" to "THOROUGHBRED",
                        "date_of_birth" to "2024-04-10",
                        "breeder" to "ノーザンファーム",
                        "microchip_number" to "392140000000456",
                        "dna_parentage" to "CONSISTENT",
                        "registration_number" to "E2E-FOAL-001",
                    )
                )
                .exchange()
                .expectStatus()
                .isCreated
                .expectBody()
                .jsonPath("$.id")
                .isNotEmpty
                .returnResult()
                .responseBody
        val foalId = JsonPath.read<String>(String(createdBody!!), "$.id")

        // 照会（実 DB から別リクエストで読み戻す）。一覧のサマリではなく完全表現が返ることまで見る。
        restTestClient
            .get()
            .uri("/api/bloodHorses/{id}", foalId)
            .header(HttpHeaders.AUTHORIZATION, TestJwt.bearerToken())
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.id")
            .isEqualTo(foalId)
            .jsonPath("$.registration_number")
            .isEqualTo("E2E-FOAL-001")
            .jsonPath("$.breeder")
            .isEqualTo("ノーザンファーム")
            // マイクロチップ番号は審査テーブルから JOIN で引く列。読み取り経路の JOIN が効いていること。
            .jsonPath("$.microchip_number")
            .isEqualTo("392140000000456")
            // 出自の discriminated union が実 HTTP でも壊れずに往復すること。
            .jsonPath("$.origin.type")
            .isEqualTo("DOMESTIC")
            .jsonPath("$.origin.sire_id")
            .isEqualTo(sireId)
            .jsonPath("$.origin.dam_id")
            .isEqualTo(damId)
            // 血統登録の直後は未命名。Jackson は default-property-inclusion 未設定（= ALWAYS）なので
            // name は省略されず null として出力される。null と空文字の双方を許す isEmpty で受ける。
            .jsonPath("$.name")
            .isEmpty
    }

    /** 父母不明の輸入馬を血統登録し、その ID を返す（内国産馬の血統登録に要る父母の seed 用）。 */
    private fun registerImportedHorse(sex: String, registrationNumber: String): String {
        val body =
            restTestClient
                .post()
                .uri("/api/bloodHorses:registerImported")
                .header(HttpHeaders.AUTHORIZATION, TestJwt.bearerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                    mapOf(
                        "sex" to sex,
                        "coat_color" to "BAY",
                        "breed_type" to "THOROUGHBRED",
                        "date_of_birth" to "2015-03-20",
                        "breeder" to "Coolmore",
                        "microchip_number" to
                            "3921400000${if (sex == "MALE") "10001" else "10002"}",
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
