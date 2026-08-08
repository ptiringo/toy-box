package com.example.api.e2e

import com.example.api.support.TestJwt
import com.jayway.jsonpath.JsonPath
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.client.RestTestClient

/**
 * `:provision` を実行し、その subject の「はじまりの世界」のIDを返す（#705）。
 *
 * ドメイン API は `/api/worlds/{worldId}/...` に居るため、E2E は必ず世界のIDを先に手に入れる必要がある。 `:provision` の応答は
 * `accountId` しか返さないので、`GET /api/worlds` の先頭（id 昇順 ＝ 作成順）から引く。
 *
 * @param subject トークンの `sub`。既定以外を渡せば「他人」として振る舞える
 */
fun RestTestClient.provisionAndFirstWorldId(subject: String = "test-user"): String {
    val token = TestJwt.bearerToken(subject)
    post()
        .uri("/api/me:provision")
        .header(HttpHeaders.AUTHORIZATION, token)
        .exchange()
        .expectStatus()
        .is2xxSuccessful
    val body =
        get()
            .uri("/api/worlds")
            .header(HttpHeaders.AUTHORIZATION, token)
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .returnResult()
            .responseBody
    return JsonPath.read<String>(String(body!!), "$[0].id")
}
