package com.example.api.controller

import java.net.URI
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

/**
 * 横断的な例外ハンドラ兼、全エラーレスポンスの描画 funnel。
 *
 * すべての HTTP エラー応答（業務ルール違反・フレームワーク標準例外・想定外例外）はここを通り、RFC 9457 (`application/problem+json`) 形式に統一される。
 * - 業務ルール違反は各 Controller 境界で `Result` を [ProblemDetail] にマップし、`orThrowProblem()` で
 *   [org.springframework.web.ErrorResponseException] として送出される。本ハンドラ（基底
 *   [ResponseEntityExceptionHandler]）が ProblemDetail をそのまま描画する。
 * - Spring MVC 標準例外（リクエストボディ不正・媒体型不一致等）も基底クラスが ProblemDetail へ変換する。
 * - 上記以外の想定外例外（インフラ障害・プログラミングエラー）は 500 + problem+json で返す。
 *
 * problem 形の一元化のため、[handleExceptionInternal] で `type` / `errorCode` 規約を未設定の ProblemDetail に
 * 一律で付与する（業務エラーは [ConventionalProblemDetail] 型で規約済みのため二重付与しない）。
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
        return problem(
            status = HttpStatus.INTERNAL_SERVER_ERROR,
            code = "internal-server-error",
            title = "Internal server error",
            detail = "サーバー内部でエラーが発生しました。",
        )
    }

    /**
     * 基底クラスが組み立てた標準例外の応答に、本プロジェクトの RFC 9457 規約（`type` / `errorCode`）を付与する。
     *
     * [ConventionalProblemDetail] 型の ProblemDetail（業務エラー由来）は規約済みとみなして触らない。
     */
    override fun handleExceptionInternal(
        ex: Exception,
        body: Any?,
        headers: HttpHeaders,
        statusCode: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val response = super.handleExceptionInternal(ex, body, headers, statusCode, request)
        (response?.body as? ProblemDetail)?.let(::applyConvention)
        return response
    }

    private fun applyConvention(problem: ProblemDetail) {
        // 規約済みかは errorCode プロパティ名ではなく型で判定する（プロパティ名一致を単一の弱点にしない）
        if (problem is ConventionalProblemDetail) {
            return
        }
        // 標準ステータスは enum 名から、非標準コードは数値からコードを導く（funnel 内で valueOf 例外を出さない）
        val code =
            HttpStatus.resolve(problem.status)?.name?.lowercase()?.replace('_', '-')
                ?: "http-${problem.status}"
        if (problem.type == BLANK_TYPE) {
            problem.type = URI.create("urn:problem-type:$code")
        }
        problem.setProperty("error_code", code)
    }

    companion object {
        private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)
        private val BLANK_TYPE = URI.create("about:blank")
    }
}
