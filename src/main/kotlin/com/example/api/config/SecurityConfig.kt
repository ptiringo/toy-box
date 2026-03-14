package com.example.api.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.header.XFrameOptionsServerHttpHeadersWriter

/**
 * セキュリティ設定
 *
 * セキュリティヘッダーの追加とCORS設定を行う
 */
@Configuration
@EnableWebFluxSecurity
class SecurityConfig {
    @Bean
    fun securityWebFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain =
        http
            .csrf { it.disable() } // WebFlux APIではCSRFは無効化（必要に応じて有効化）
            .authorizeExchange { exchanges ->
                exchanges
                    .pathMatchers("/actuator/health")
                    .permitAll() // ヘルスチェックは認証不要
                    .pathMatchers("/api/**")
                    .permitAll() // API エンドポイントは認証不要（必要に応じて変更）
                    .pathMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**")
                    .permitAll() // Swagger UIは認証不要
                    .anyExchange()
                    .permitAll() // その他は全て許可（開発環境用、本番では要検討）
            }.headers { headers ->
                headers
                    .frameOptions { it.mode(XFrameOptionsServerHttpHeadersWriter.Mode.DENY) } // クリックジャッキング対策
                    .contentSecurityPolicy { it.policyDirectives("default-src 'self'") } // CSP設定
                    .referrerPolicy { } // Referrer-Policy
                    .permissionsPolicy { it.policy("geolocation=(), microphone=(), camera=()") } // Permissions-Policy
            }.build()
}
