package com.example.api.e2e

import com.example.api.support.TestJwt
import com.jayway.jsonpath.JsonPath
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient

/*
 * breeding 系 E2E（繁殖登録・繁殖成績・種付成績報告）が共通で要る seed 手順。
 *
 * 繁殖の書き込み経路は「血統登録済みの馬 → 繁殖登録 → 年次成績 / 年次報告」と段階的に前提が積み上がるため、
 * どのリソースの往復を試すにも先行する段の Create を HTTP 越しに踏む必要がある。3 本の E2E で同じ手順を
 * 書き写さないよう、ここに寄せる（E2E はゲート外のソースセット。testing.md）。
 */

/** 父母不明の輸入馬を血統登録し、その ID を返す（繁殖登録の対象個体を用意する最短経路）。 */
fun RestTestClient.registerImportedHorse(
    worldId: String,
    sex: String,
    registrationNumber: String,
    microchipNumber: String,
): String {
    val body =
        post()
            .uri("/api/worlds/{worldId}/bloodHorses:registerImported", worldId)
            .header(HttpHeaders.AUTHORIZATION, TestJwt.bearerToken())
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                mapOf(
                    "sex" to sex,
                    "coat_color" to "BAY",
                    "breed_type" to "THOROUGHBRED",
                    "date_of_birth" to "2015-03-20",
                    "breeder" to "Coolmore",
                    "microchip_number" to microchipNumber,
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

/**
 * 馬を血統登録したうえで繁殖登録を成立させ、繁殖登録の ID を返す。
 *
 * 付与されるロール（種牡馬／繁殖牝馬）は対象個体の性から定まるため、[sex] で作り分ける。
 */
fun RestTestClient.registerBreedingRegistration(
    worldId: String,
    sex: String,
    registrationNumber: String,
    microchipNumber: String,
    breedingRegistrationNumber: String,
): String {
    val horseId = registerImportedHorse(worldId, sex, registrationNumber, microchipNumber)
    val body =
        post()
            .uri("/api/worlds/{worldId}/breedingRegistrations", worldId)
            .header(HttpHeaders.AUTHORIZATION, TestJwt.bearerToken())
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                mapOf(
                    "blood_horse_id" to horseId,
                    "registration_number" to breedingRegistrationNumber,
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
