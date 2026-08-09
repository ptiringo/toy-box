package com.example.api.controller.me.problem

import com.example.api.application.iam.me.ProvisionMeError
import com.example.api.controller.problem
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail

/**
 * 初回セットアップの失敗を RFC 9457 の problem+json へ写す。
 *
 * [ProvisionMeError.InvalidSubject] と [ProvisionMeError.InvalidDefaultWorldName] は、トークン検証を
 * 通っていれば／定数を正しく設定していれば起きない防御的分岐。クライアントに直せることが無いので 500 とし、 400 系で「あなたの入力が悪い」と誤って伝えない。
 *
 * 競合（409）のバリアントは持たない。並行するセットアップは DB の UNIQUE を裁定者にして吸収され、 呼び出し側に競合として見えないため（#713）。
 */
fun ProvisionMeError.toProblemDetail(): ProblemDetail =
    when (this) {
        is ProvisionMeError.InvalidSubject ->
            problem(
                status = HttpStatus.INTERNAL_SERVER_ERROR,
                code = "invalid-subject",
                title = "Invalid subject",
                detail = "検証済みトークンの sub が不正です。",
            )
        is ProvisionMeError.InvalidDefaultWorldName ->
            problem(
                status = HttpStatus.INTERNAL_SERVER_ERROR,
                code = "invalid-default-world-name",
                title = "Invalid default world name",
                detail = "既定の世界名がサーバ側の不変条件を満たしていません。",
            )
    }
