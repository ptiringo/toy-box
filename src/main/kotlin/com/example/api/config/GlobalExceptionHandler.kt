package com.example.api.config

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.LocalDateTime

/**
 * グローバルエラーハンドラー
 *
 * アプリケーション全体の例外を統一的に処理し、適切なHTTPレスポンスを返す
 */
@RestControllerAdvice
class GlobalExceptionHandler {
    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    data class ErrorResponse(
        val timestamp: LocalDateTime,
        val status: Int,
        val error: String,
        val message: String,
    )

    /**
     * 不正な引数の例外を処理する
     */
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgumentException(ex: IllegalArgumentException): ResponseEntity<ErrorResponse> {
        logger.warn("不正な引数: ${ex.message}", ex)
        val response =
            ErrorResponse(
                timestamp = LocalDateTime.now(),
                status = HttpStatus.BAD_REQUEST.value(),
                error = HttpStatus.BAD_REQUEST.reasonPhrase,
                message = ex.message ?: "不正な引数です",
            )
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response)
    }

    /**
     * 不正な状態の例外を処理する
     */
    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalStateException(ex: IllegalStateException): ResponseEntity<ErrorResponse> {
        logger.warn("不正な状態: ${ex.message}", ex)
        val response =
            ErrorResponse(
                timestamp = LocalDateTime.now(),
                status = HttpStatus.CONFLICT.value(),
                error = HttpStatus.CONFLICT.reasonPhrase,
                message = ex.message ?: "不正な状態です",
            )
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response)
    }

    /**
     * 要素が見つからない例外を処理する
     */
    @ExceptionHandler(NoSuchElementException::class)
    fun handleNoSuchElementException(ex: NoSuchElementException): ResponseEntity<ErrorResponse> {
        logger.warn("要素が見つかりません: ${ex.message}", ex)
        val response =
            ErrorResponse(
                timestamp = LocalDateTime.now(),
                status = HttpStatus.NOT_FOUND.value(),
                error = HttpStatus.NOT_FOUND.reasonPhrase,
                message = ex.message ?: "要素が見つかりません",
            )
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response)
    }

    /**
     * その他の予期しない例外を処理する
     */
    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception): ResponseEntity<ErrorResponse> {
        logger.error("予期しないエラーが発生しました: ${ex.message}", ex)
        val response =
            ErrorResponse(
                timestamp = LocalDateTime.now(),
                status = HttpStatus.INTERNAL_SERVER_ERROR.value(),
                error = HttpStatus.INTERNAL_SERVER_ERROR.reasonPhrase,
                message = "予期しないエラーが発生しました",
            )
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response)
    }
}
