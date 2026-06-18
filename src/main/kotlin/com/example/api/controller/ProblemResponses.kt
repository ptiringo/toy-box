package com.example.api.controller

import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getOrElse
import java.net.URI
import org.springframework.http.HttpStatus
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
 *     .toResponse()
 * ```
 */
fun <T> Result<T, ProblemDetail>.orThrowProblem(): T = getOrElse { problem ->
    throw ErrorResponseException(HttpStatusCode.valueOf(problem.status), problem, null)
}

/**
 * RFC 9457 規約に従って [ProblemDetail] を組み立てる唯一のビルダ。
 *
 * `type` は暫定の `urn:problem-type:{code}` 形式、拡張プロパティ `errorCode` にアプリ固有のエラーコードを持たせる。 各 Controller の
 * `toProblemDetail()` 群と [GlobalExceptionHandler] の 500 応答が共有し、problem の形を変えるときは ここだけを直せばよい（例:
 * `urn:` を resolvable URI に差し替える）。
 *
 * @param status 業務ルール違反の意味に応じた HTTP ステータス
 * @param code kebab-case のエラーコード（`type` と `errorCode` の双方に用いる）
 * @param title 人間可読の短い説明
 * @param detail 個別の事象に応じた追加の説明
 */
internal fun problem(
    status: HttpStatus,
    code: String,
    title: String,
    detail: String,
): ProblemDetail =
    ProblemDetail.forStatus(status).apply {
        type = URI.create("urn:problem-type:$code")
        this.title = title
        this.detail = detail
        setProperty("errorCode", code)
    }
