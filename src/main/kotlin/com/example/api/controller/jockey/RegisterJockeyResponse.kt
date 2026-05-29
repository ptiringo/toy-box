package com.example.api.controller.jockey

import com.example.api.application.horseracing.jockey.JockeyRegistrationError
import com.example.api.domain.horseracing.jockey.JockeyValidationError
import java.net.URI
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail

/**
 * `POST /api/jockeys` の成功レスポンスボディ。
 *
 * @property id 登録された [com.example.api.domain.horseracing.jockey.JockeyId] の生 UUID
 * @property firstName 名
 * @property lastName 姓
 */
data class RegisterJockeyResponse(val id: UUID, val firstName: String, val lastName: String)

/**
 * [JockeyRegistrationError] を RFC 9457 (`application/problem+json`) 形式の [ProblemDetail] に 変換する。
 *
 * - `type`: `urn:problem-type:{kebab-case-code}` 形式の暫定 URI
 * - `title`: 人間可読の短い説明
 * - `status`: 業務ルール違反の意味に応じた HTTP ステータス
 * - `detail`: 個別の事象に応じた追加の説明
 * - 拡張プロパティ `errorCode`: アプリケーション固有のエラーコード
 */
fun JockeyRegistrationError.toProblemDetail(): ProblemDetail =
    when (this) {
        is JockeyRegistrationError.InvalidJockey -> cause.toProblemDetail()
        is JockeyRegistrationError.DuplicateJockey ->
            problem(
                    status = HttpStatus.CONFLICT,
                    code = "duplicate-jockey",
                    title = "Duplicate jockey",
                    detail = "同姓同名のジョッキーが既に登録されています。",
                )
                .apply { setProperty("existingId", existingId.value) }
    }

private fun JockeyValidationError.toProblemDetail(): ProblemDetail =
    when (this) {
        JockeyValidationError.BlankFirstName ->
            problem(
                status = HttpStatus.BAD_REQUEST,
                code = "blank-first-name",
                title = "First name is blank",
                detail = "firstName は空であってはいけません。",
            )
        JockeyValidationError.BlankLastName ->
            problem(
                status = HttpStatus.BAD_REQUEST,
                code = "blank-last-name",
                title = "Last name is blank",
                detail = "lastName は空であってはいけません。",
            )
    }

private fun problem(
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
