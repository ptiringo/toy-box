package com.example.api.support

import java.time.Instant
import java.time.temporal.ChronoUnit
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jwt.JwtException

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
    fun `期限切れのトークンは拒否する`() {
        val token =
            EmulatorJwt.unsignedToken(
                subject = "emulator-uid",
                issuer = issuer,
                audience = audience,
                expiresAt = Instant.now().minus(1, ChronoUnit.MINUTES),
            )

        val thrown = runCatching { decoder.decode(token) }.exceptionOrNull()

        assert(thrown is JwtException)
    }

    @Test
    fun `JWT の体裁でない文字列は拒否する`() {
        val thrown = runCatching { decoder.decode("not-a-jwt") }.exceptionOrNull()

        assert(thrown is JwtException)
    }
}
