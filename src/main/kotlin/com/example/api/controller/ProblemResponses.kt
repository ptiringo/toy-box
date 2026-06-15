package com.example.api.controller

import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getOrElse
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.web.ErrorResponseException

/**
 * [ProblemDetail] にマップ済みの失敗を [ErrorResponseException] として送出し、中央のエラーハンドラ
 * （[GlobalExceptionHandler]）に描画を委譲する。成功なら値をそのまま返す。
 *
 * これにより業務エラーもフレームワーク/インフラ例外と同じ funnel を通り、RFC 9457 形式の一貫した problem+json として描画される。ドメイン/アプリケーション層は
 * Result-first を保ち、例外化は Controller 境界のこの一手に限る（方針は `.claude/rules/error-handling.md`）。
 *
 * 典型的な使い方:
 * ```
 * registerInStudBook(command)
 *     .mapError { it.toProblemDetail() } // ドメイン/アプリエラー → ProblemDetail（アダプタの方針）
 *     .orThrowProblem()                  // Err なら funnel へ送出、Ok なら値を取り出す
 *     .toRegisterResponse()
 * ```
 */
fun <T> Result<T, ProblemDetail>.orThrowProblem(): T = getOrElse { problem ->
    throw ErrorResponseException(HttpStatusCode.valueOf(problem.status), problem, null)
}
