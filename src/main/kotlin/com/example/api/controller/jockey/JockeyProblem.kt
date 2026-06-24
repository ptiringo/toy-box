package com.example.api.controller.jockey

import com.example.api.application.racing.jockey.JockeyRegistrationError
import com.example.api.controller.problem
import com.example.api.domain.racing.model.jockey.JockeyValidationError
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail

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
                .apply { setProperty("existing_id", existingId.value) }
    }

private fun JockeyValidationError.toProblemDetail(): ProblemDetail =
    when (this) {
        JockeyValidationError.BlankFirstName ->
            problem(
                status = HttpStatus.BAD_REQUEST,
                code = "blank-first-name",
                title = "First name is blank",
                detail = "first_name は空であってはいけません。",
            )
        JockeyValidationError.BlankLastName ->
            problem(
                status = HttpStatus.BAD_REQUEST,
                code = "blank-last-name",
                title = "Last name is blank",
                detail = "last_name は空であってはいけません。",
            )
    }
