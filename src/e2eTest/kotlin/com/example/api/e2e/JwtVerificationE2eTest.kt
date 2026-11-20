package com.example.api.e2e

import com.example.api.support.PostgresContainerSupport
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import org.junit.jupiter.api.Test
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestConstructor
import org.springframework.test.context.TestConstructor.AutowireMode
import org.springframework.test.web.servlet.client.RestTestClient

/**
 * 本番と同じ自動構成の `JwtDecoder` が、ID トークンを本当に検証していることを確かめる E2E（#813）。
 *
 * 他のテストは `TestJwtDecoderConfiguration`（HS256）で decoder を差し替えるため、本番の経路（`issuer-uri` からの OIDC
 * discovery → JWKS の取得 → RS256 署名・issuer・audience・有効期限の検証。ADR-0064）を通すテストはここだけである。
 *
 * GCP Identity Platform には届かないので、JDK 同梱の HTTP サーバで偽の issuer（discovery と JWKS）を立てる。差し替えるのは 次の 2
 * つだけで、decoder は Spring Boot の自動構成に作らせる。
 * - `issuer-uri`: 偽の issuer へ向ける（本番の値は Google のホストに固定されていて差し替えようがない）
 * - `GCP_PROJECT_ID`: `toy-box-e2e`。`audiences` は差し替えず、`application.yml` の式から解決させる
 *
 * 各 401 のケースは、正常系のトークンから 1 点だけを変えたものにする。複数を同時に崩すと、どの検証が効いたのか区別できない。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@TestConstructor(autowireMode = AutowireMode.ALL)
class JwtVerificationE2eTest(val restTestClient: RestTestClient) : PostgresContainerSupport() {

    companion object {
        private const val KEY_ID = "e2e-key"
        private const val PROJECT_ID = "toy-box-e2e"

        /** 偽の issuer が JWKS で公開する鍵。 */
        private val signingKey: RSAKey = RSAKeyGenerator(2048).keyID(KEY_ID).generate()

        /** JWKS に載っていない鍵。`kid` は揃えて、鍵の取り違えではなく署名の不一致で弾かれる状況を作る。 */
        private val foreignKey: RSAKey = RSAKeyGenerator(2048).keyID(KEY_ID).generate()

        private val issuerServer: HttpServer =
            HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
                createContext("/.well-known/openid-configuration") { it.respondJson(discovery()) }
                createContext("/jwks") { it.respondJson(jwks()) }
                start()
            }

        private val issuerUri = "http://127.0.0.1:${issuerServer.address.port}"

        private fun jwks() = JWKSet(signingKey.toPublicJWK()).toString()

        private fun discovery() = """{"issuer":"$issuerUri","jwks_uri":"$issuerUri/jwks"}"""

        private fun HttpExchange.respondJson(body: String) {
            val bytes = body.toByteArray()
            responseHeaders.add("Content-Type", "application/json")
            sendResponseHeaders(200, bytes.size.toLong())
            responseBody.use { it.write(bytes) }
        }

        @JvmStatic
        @DynamicPropertySource
        fun issuerProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri") { issuerUri }
            registry.add("GCP_PROJECT_ID") { PROJECT_ID }
        }
    }

    private fun token(
        key: RSAKey = signingKey,
        iss: String = issuerUri,
        aud: String = PROJECT_ID,
        expiresAt: Instant = Instant.now().plus(10, ChronoUnit.MINUTES),
    ): String {
        val claims =
            JWTClaimsSet.Builder()
                .subject("sub-rs256")
                .issuer(iss)
                .audience(aud)
                .issueTime(Date.from(Instant.now().minus(15, ChronoUnit.MINUTES)))
                .expirationTime(Date.from(expiresAt))
                .build()
        val header = JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.keyID).build()
        return SignedJWT(header, claims).apply { sign(RSASSASigner(key)) }.serialize()
    }

    private fun provision(token: String) =
        restTestClient
            .post()
            .uri("/api/me:provision")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .exchange()

    @Test
    fun `JWKS の鍵で署名した正しいトークンは通る`() {
        provision(token()).expectStatus().isOk
    }

    @Test
    fun `JWKS に無い鍵で署名したトークンは 401`() {
        provision(token(key = foreignKey)).expectStatus().isUnauthorized
    }

    @Test
    fun `issuer が違うトークンは 401`() {
        provision(token(iss = "https://securetoken.google.com/other-project"))
            .expectStatus()
            .isUnauthorized
    }

    @Test
    fun `audience が違うトークンは 401`() {
        provision(token(aud = "other-project")).expectStatus().isUnauthorized
    }

    @Test
    fun `有効期限が切れたトークンは 401`() {
        provision(token(expiresAt = Instant.now().minus(10, ChronoUnit.MINUTES)))
            .expectStatus()
            .isUnauthorized
    }
}
