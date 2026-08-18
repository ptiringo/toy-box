package com.example.api.support

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.PlainHeader
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.PlainJWT
import java.time.Instant
import java.util.Date
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.jwt.BadJwtException
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtClaimNames
import org.springframework.security.oauth2.jwt.JwtClaimValidator
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtIssuerValidator
import org.springframework.security.oauth2.jwt.JwtValidationException
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.MappedJwtClaimSetConverter

/**
 * Firebase Auth Emulator が発行する **未署名** ID トークンを受理する [JwtDecoder]。
 *
 * **このクラスを `src/main` へ移してはならない。** 署名を検証しないため、本番の依存に載った瞬間に誰でも任意の `sub` を名乗れる。`src/test` に置いたまま
 * `bootTestRun`（test runtime classpath でアプリを起動する Gradle タスク）から のみ効かせることで、本番成果物に穴を開けずにブラウザ E2E
 * を成立させている（#725）。
 *
 * Emulator が未署名トークンを出すのは Firebase の仕様で、公式ドキュメントも "the Authentication emulator issues unsigned ID
 * tokens, which are only accepted by other Firebase emulators, or the Firebase Admin SDK when
 * configured" と明記している。したがって署名検証は構造的に不可能で、その一点だけを射程外にする。
 *
 * **省くのは署名検証だけ**で、issuer / audience / 有効期限は本番と同じ validator を掛ける。これにより「フロントの projectId とバックの
 * `GCP_PROJECT_ID` がズレていて 401 になる」という実際に起きた事故が E2E の射程に入る。
 */
class EmulatorJwtDecoder(issuer: String, audience: String) : JwtDecoder {

    private val validator: OAuth2TokenValidator<Jwt> =
        DelegatingOAuth2TokenValidator(
            // Spring Security 6.5 系で JwtValidators.createDefaultWithIssuer(issuer) が deprecated
            // になったため、既定の検証（exp 等）+ issuer 検証を明示合成する形に読み替えている。
            // 意味（既定の検証 + issuer 検証）は変えていない。
            JwtValidators.createDefaultWithValidators(JwtIssuerValidator(issuer)),
            JwtClaimValidator<List<String>>(JwtClaimNames.AUD) { aud -> aud.contains(audience) },
        )

    override fun decode(token: String): Jwt {
        val jwt = parseJwt(token)
        val result = validator.validate(jwt)
        if (result.hasErrors()) {
            throw JwtValidationException("Emulator トークンの検証に失敗した", result.errors)
        }
        return jwt
    }

    /**
     * トークン文字列を [Jwt] へ変換する。detekt `ThrowsCount`（1 関数あたり throw は 2 つまで）に収めるため [decode]
     * から切り出した。パース失敗・claims 組み立て失敗のどちらも [BadJwtException] へ寄せる。
     */
    private fun parseJwt(token: String): Jwt {
        val parsed = runCatching {
            PlainJWT.parse(token)
        }
            .getOrElse { throw BadJwtException("Emulator の未署名トークンとして読めない: ${it.message}", it) }
        // exp / iat を Instant に、aud を List<String> に写す。NimbusJwtDecoder が使うのと同じ既定コンバータ。
        val claims = CLAIM_SET_CONVERTER.convert(parsed.jwtClaimsSet.toJSONObject())
        // Jwt.Builder#build() は「exp が iat より後」を自前で検証し、破ると IllegalArgumentException を投げる
        // （JwtException ではない）。ここで拾うのは exp < iat という構造的に壊れたトークン（正規の Emulator
        // トークンでは起こり得ない）を JwtException 系へ寄せて拒否するための防御。正常な形（iat < exp）の
        // 期限切れトークンはこの分岐を通らず build() を通過し、validator（JwtTimestampValidator）が拒否する。
        return runCatching {
            Jwt.withTokenValue(token)
                .headers { it.putAll(parsed.header.toJSONObject()) }
                .claims { it.putAll(claims) }
                .build()
        }
            .getOrElse {
                throw BadJwtException("Emulator トークンの claims を組み立てられない: ${it.message}", it)
            }
    }

    private companion object {
        private val CLAIM_SET_CONVERTER = MappedJwtClaimSetConverter.withDefaults(emptyMap())
    }
}

/**
 * [EmulatorJwtDecoder] を `JwtDecoder` Bean として差し込む。
 *
 * Spring Boot の `JwtDecoder` 自動構成は `@ConditionalOnMissingBean` なので、この Bean があれば本番の OIDC
 * discovery（GCP への通信）は走らない。検証パラメータは `application.yml` の値をそのまま引くため、 issuer / audience
 * の期待値が本番と同じ経路で決まる。
 */
@TestConfiguration(proxyBeanMethods = false)
class EmulatorJwtDecoderConfiguration {
    @Bean
    fun jwtDecoder(
        @Value("\${spring.security.oauth2.resourceserver.jwt.issuer-uri}") issuer: String,
        @Value("\${spring.security.oauth2.resourceserver.jwt.audiences}") audience: String,
    ): JwtDecoder = EmulatorJwtDecoder(issuer, audience)
}

/** テストから Emulator と同じ形の未署名トークンを組み立てる。 */
object EmulatorJwt {
    /**
     * @param issuedAt 既定は現在時刻。「exp < iat」の構造的に壊れたトークンを組み立てたいときだけ、 `expiresAt` より後の値を明示的に渡す（正規の
     *   Emulator トークンでは iat < exp が常に成り立つ）。
     */
    fun unsignedToken(
        subject: String,
        issuer: String,
        audience: String,
        expiresAt: Instant,
        issuedAt: Instant = Instant.now(),
    ): String {
        val claims =
            JWTClaimsSet.Builder()
                .subject(subject)
                .issuer(issuer)
                .audience(audience)
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(expiresAt))
                .build()
        // 実 Emulator のヘッダは `{"alg":"none","typ":"JWT"}`（Task 1 で実測）。`typ` を省くと
        // JwtTypeValidator が typ 欠落を許すぶん、実物より緩い形を検証することになるので実形に揃える。
        return PlainJWT(PlainHeader.Builder().type(JOSEObjectType.JWT).build(), claims).serialize()
    }
}
