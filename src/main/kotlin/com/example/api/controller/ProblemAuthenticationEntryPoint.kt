package com.example.api.controller

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.web.ErrorResponseException
import org.springframework.web.servlet.HandlerExceptionResolver

/**
 * 認証されていないリクエスト（トークン無し・不正なトークン）を 401 + `application/problem+json` で返す入口。
 *
 * セキュリティフィルタは DispatcherServlet の外側で走るため、そのままでは Spring MVC の中央エラー funnel
 * （[GlobalExceptionHandler]）を通らず、Servlet コンテナ既定の空ボディ 401 になってしまう。ここで [problem] を組み立てた
 * [ErrorResponseException] を `handlerExceptionResolver` へ手渡すことで、業務エラーと同じ funnel・同じ RFC 9457
 * の形に揃える（problem の描画点を 1 つに保つ）。
 *
 * WWW-Authenticate ヘッダは RFC 6750 が 401 に求める challenge であり、resolver へ渡す前に自分で載せる （funnel
 * は認証スキームを知らないため）。
 */
internal class ProblemAuthenticationEntryPoint(private val resolver: HandlerExceptionResolver) :
    AuthenticationEntryPoint {

    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException,
    ) {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, BEARER_CHALLENGE)
        val problem =
            problem(
                status = HttpStatus.UNAUTHORIZED,
                code = "unauthenticated",
                title = "Unauthenticated",
                detail = "この API の呼び出しには有効な ID トークンが必要です。",
            )
        resolver.resolveException(
            request,
            response,
            null,
            ErrorResponseException(HttpStatusCode.valueOf(problem.status), problem, authException),
        )
    }

    private companion object {
        const val BEARER_CHALLENGE = "Bearer"
    }
}
