package com.example.api.support

import java.time.Instant
import java.time.temporal.ChronoUnit
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jwt.BadJwtException
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.security.oauth2.jwt.JwtValidationException

class EmulatorJwtDecoderTest {
    private val issuer = "https://securetoken.google.com/toy-box-e2e"
    private val audience = "toy-box-e2e"
    private val decoder = EmulatorJwtDecoder(issuer, audience)

    @Test
    fun `Emulator が発行する形の未署名トークンを受理し sub を取り出す`() {
        val token =
            EmulatorJwt.unsignedToken(
                subject = "emulator-uid",
                issuer = issuer,
                audience = audience,
                expiresAt = Instant.now().plus(10, ChronoUnit.MINUTES),
            )

        val jwt = decoder.decode(token)

        assert(jwt.subject == "emulator-uid")
    }

    @Test
    fun `issuer が異なるトークンは拒否する`() {
        val token =
            EmulatorJwt.unsignedToken(
                subject = "emulator-uid",
                issuer = "https://securetoken.google.com/someone-else",
                audience = audience,
                expiresAt = Instant.now().plus(10, ChronoUnit.MINUTES),
            )

        val thrown = runCatching { decoder.decode(token) }.exceptionOrNull()

        assert(thrown is JwtException)
    }

    @Test
    fun `audience が異なるトークンは拒否する`() {
        val token =
            EmulatorJwt.unsignedToken(
                subject = "emulator-uid",
                issuer = issuer,
                audience = "someone-else",
                expiresAt = Instant.now().plus(10, ChronoUnit.MINUTES),
            )

        val thrown = runCatching { decoder.decode(token) }.exceptionOrNull()

        assert(thrown is JwtException)
    }

    @Test
    fun `期限切れのトークンは有効期限 validator に拒否される`() {
        // iat < exp < now の「正規の形」の期限切れトークン。JwtTimestampValidator の既定 clock skew（60 秒）に
        // 対して十分な余裕（10 分）を取り、境界値の揺れでテストが不安定にならないようにする。
        val token =
            EmulatorJwt.unsignedToken(
                subject = "emulator-uid",
                issuer = issuer,
                audience = audience,
                issuedAt = Instant.now().minus(2, ChronoUnit.HOURS),
                expiresAt = Instant.now().minus(10, ChronoUnit.MINUTES),
            )

        val thrown = runCatching { decoder.decode(token) }.exceptionOrNull()

        // validator まで到達して拒否されたことを、上位型 JwtException ではなく具体的な
        // JwtValidationException で確認する。BadJwtException（parseJwt 内の構造チェック）に
        // すり替わっていないことがこのアサーションで担保される。
        assert(thrown is JwtValidationException)
    }

    @Test
    fun `exp が iat より前の構造的に壊れたトークンは組み立て時点で拒否される`() {
        // 正規の Emulator トークンでは決して起きない exp < iat。Jwt.Builder#build() の不変条件チェックで
        // 落ち、validator（JwtTimestampValidator）には到達しない防御経路を確認する。
        val token =
            EmulatorJwt.unsignedToken(
                subject = "emulator-uid",
                issuer = issuer,
                audience = audience,
                issuedAt = Instant.now(),
                expiresAt = Instant.now().minus(1, ChronoUnit.MINUTES),
            )

        val thrown = runCatching { decoder.decode(token) }.exceptionOrNull()

        assert(thrown is BadJwtException)
    }

    @Test
    fun `JWT の体裁でない文字列は拒否する`() {
        val thrown = runCatching { decoder.decode("not-a-jwt") }.exceptionOrNull()

        assert(thrown is JwtException)
    }
}
