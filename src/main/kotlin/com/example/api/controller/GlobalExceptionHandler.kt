package com.example.api.controller

import java.net.URI
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

/**
 * 横断的な例外ハンドラ。
 *
 * - Spring MVC 標準例外（リクエストボディ不正・媒体型不一致等）は親クラス [ResponseEntityExceptionHandler]
 *   に委譲する。`spring.mvc.problemdetails.enabled=true` により RFC 9457 (`application/problem+json`)
 *   形式へ変換される。
 * - 上記以外の想定外例外（インフラ障害・プログラミングエラー）は 500 + problem+json で返す。
 *
 * 業務ルール違反は各 Controller 境界で `Result` から `ProblemDetail` に変換するため、ここでは扱わない。
 */
@RestControllerAdvice
class GlobalExceptionHandler : ResponseEntityExceptionHandler() {
    /**
     * 想定外の例外を 500 (`application/problem+json`) として返す。
     *
     * 例外メッセージはセキュリティのためレスポンスに含めず、ログにのみ記録する。
     */
    @ExceptionHandler(Exception::class)
    fun handleUnexpectedException(ex: Exception): ProblemDetail {
        log.error("想定外の例外が発生しました", ex)
        return ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR).apply {
            type = URI.create("urn:problem-type:internal-server-error")
            title = "Internal server error"
            detail = "サーバー内部でエラーが発生しました。"
            setProperty("errorCode", "internal-server-error")
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)
    }
}
