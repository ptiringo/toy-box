package com.example.api.support

import com.nimbusds.jose.jwk.source.ImmutableSecret
import com.nimbusds.jose.proc.SecurityContext
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder

/**
 * 認証（OAuth2 リソースサーバ・ADR-0064）を要するテストのための、対称鍵（HS256）で自己完結する JWT 発行・検証ハーネス。
 *
 * 本番の `JwtDecoder` は `issuer-uri` から OIDC discovery で GCP Identity Platform の JWKS を引くが、テスト環境は GCP
 * へ到達できない（到達できてもトークンを発行できない）。Spring Boot の `JwtDecoder` 自動構成は `@ConditionalOnMissingBean`
 * のため、[TestJwtDecoderConfiguration] を `@Import` すれば HS256 の decoder が
 * 勝ち、ネットワークに出ずに署名検証だけを本物のフィルタチェーンで走らせられる。
 *
 * 裏返すと issuer / audience の検証（本番で Boot が組み立てる validator）はテストの射程外であり、`application.yml`
 * のプロパティが効いているかは検証していない。
 */
object TestJwt {
    /** HS256 の鍵長要件（256bit 以上）を満たす、テスト専用の固定シークレット。 */
    private const val SECRET = "toy-box-test-only-hmac-secret-key-for-hs256!!"

    private val key: SecretKey = SecretKeySpec(SECRET.toByteArray(), "HmacSHA256")

    private val encoder = NimbusJwtEncoder(ImmutableSecret<SecurityContext>(key))

    fun decoder(): JwtDecoder =
        NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build()

    /** `Authorization` ヘッダにそのまま載せられる、有効な Bearer トークン。 */
    fun bearerToken(subject: String = "test-user"): String = "Bearer ${token(subject)}"

    private fun token(subject: String): String {
        val issuedAt = Instant.now()
        val claims =
            JwtClaimsSet.builder()
                .subject(subject)
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(10, ChronoUnit.MINUTES))
                .build()
        val header = JwsHeader.with(MacAlgorithm.HS256).build()
        return encoder.encode(JwtEncoderParameters.from(header, claims)).tokenValue
    }
}

/** [TestJwt] の HS256 decoder を `JwtDecoder` Bean として差し込む。 */
@TestConfiguration(proxyBeanMethods = false)
class TestJwtDecoderConfiguration {
    @Bean fun jwtDecoder(): JwtDecoder = TestJwt.decoder()
}
