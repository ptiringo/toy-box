package com.example.api.controller

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.servlet.HandlerExceptionResolver

/**
 * OAuth2 リソースサーバとしての認証設定（ADR-0064）。
 *
 * 認証は GCP Identity Platform に委譲し、本 API は Bearer トークン（ID トークン = JWT）の検証だけを担う。 検証パラメータ（issuer /
 * audience）は `application.yml` の `spring.security.oauth2.resourceserver.jwt.*` が供給し、`JwtDecoder` は
 * Spring Boot の自動構成が issuer の OIDC discovery から組み立てる（`@ConditionalOnMissingBean` のため、テストは自前の
 * `JwtDecoder` Bean で差し替えられる）。
 *
 * **認可（何をしてよいか）はフィルタ層で判断しない。** ロール・権限の出所は自前 DB であり、認可は application
 * 層が担う。したがってここでの規則は「[PUBLIC_ENDPOINTS] は誰でも / それ以外は認証済みなら通す」の 2 種類だけで、認証済みユーザーが 403
 * になる経路は存在しない（`AccessDeniedHandler` を置いていないのはこのため）。
 *
 * 本クラスが `config` ではなく adapter リング（`controller`）に居るのは、401 の RFC 9457 応答を組み立てる [problem] ビルダが adapter
 * リングにあるため。内側のリングから参照すると ArchUnit の `onionLayers` に違反する。 HTTP セキュリティは adapter の関心事でもあり、配置としても正しい。
 *
 * `@ConditionalOnWebApplication` は必須。`HttpSecurity` は servlet Web コンテキストでしか Bean 化されないため、 これが無いと
 * `webEnvironment = NONE` の `@SpringBootTest`（永続化の契約テスト等）が軒並みコンテキスト起動に失敗する。 Boot 自身の Web
 * セキュリティ設定も同じ条件で守られている。
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class SecurityConfig {

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        problemEntryPoint: AuthenticationEntryPoint,
        environment: Environment,
    ): SecurityFilterChain {
        http {
            // Bearer トークンによるステートレス認証。セッション（Cookie）を発行しないため CSRF 対策は不要。
            csrf { disable() }
            sessionManagement { sessionCreationPolicy = SessionCreationPolicy.STATELESS }
            authorizeHttpRequests {
                publicEndpoints(environment).forEach { authorize(it, permitAll) }
                authorize(anyRequest, authenticated)
            }
            oauth2ResourceServer {
                jwt {}
                authenticationEntryPoint = problemEntryPoint
            }
            // トークンを一切持たないリクエストは認可判定で弾かれ ExceptionTranslationFilter を通るため、
            // そちらの入口にも同じ entry point を据えて 401 の描画を 1 つに揃える。
            exceptionHandling { authenticationEntryPoint = problemEntryPoint }
        }
        return http.build()
    }

    /**
     * 認証失敗（401）を RFC 9457 の `application/problem+json` で描画する入口。
     *
     * 描画そのものは [GlobalExceptionHandler]（中央 funnel）へ委譲するため `handlerExceptionResolver` を注入する。
     * セキュリティフィルタは DispatcherServlet の外側で走るので、resolver を明示的に呼んで funnel に載せる。
     */
    @Bean
    fun problemAuthenticationEntryPoint(
        @Qualifier("handlerExceptionResolver") resolver: HandlerExceptionResolver
    ): AuthenticationEntryPoint = ProblemAuthenticationEntryPoint(resolver)

    companion object {
        /**
         * 認証を要求しないエンドポイント。運用・CI が壊れるものだけに絞る（ADR-0064）。
         * - actuator の health: Cloud Run のヘルスチェックがトークンを持てない
         * - OpenAPI ドキュメントと Swagger UI: `generateOpenApiDocs` が forked bootRun 経由で取得するため、 認証を掛けると
         *   OpenAPI lint の CI ゲート（`lintOpenApiDocs`）が壊れる
         */
        private val PUBLIC_ENDPOINTS =
            listOf(
                "/actuator/health",
                "/v3/api-docs",
                "/v3/api-docs/**",
                "/swagger-ui.html",
                "/swagger-ui/**",
            )

        /**
         * MCP エンドポイント。MCP クライアントにトークンを載せる術がまだないため permitAll だが、 **`local`
         * プロファイルのときだけ**公開する（#712）。既定プロファイルでは `spring.ai.mcp.server.enabled: false`
         * によりエンドポイント自体が存在しないため、ここで足さないことは 実質的には意図の表明にあたる。
         */
        private const val MCP_ENDPOINTS = "/mcp/**"

        /** 実行中のプロファイルに応じた permitAll 対象。 */
        private fun publicEndpoints(environment: Environment): List<String> =
            if (environment.acceptsProfiles(Profiles.of("local"))) {
                PUBLIC_ENDPOINTS + MCP_ENDPOINTS
            } else {
                PUBLIC_ENDPOINTS
            }
    }
}
