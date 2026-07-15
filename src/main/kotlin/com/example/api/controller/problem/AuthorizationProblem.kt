package com.example.api.controller.problem

import com.example.api.controller.problem
import com.example.api.domain.shared.Permission
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail

/**
 * 権限不足（403）の共通描画。各ユースケースの `Forbidden` から一様にここへ写す。
 *
 * 必要な権限は応答に含める（何が足りないかを示さないと呼び出し側が直せない）。認証自体の失敗（401） は `ProblemAuthenticationEntryPoint` が描く。
 */
internal fun forbidden(permission: Permission): ProblemDetail =
    problem(
        status = HttpStatus.FORBIDDEN,
        code = "forbidden",
        title = "権限がありません",
        detail = "この操作には ${permission.value} の権限が必要です。",
    )

/** IdP は認証したが、この API の `account` が無い（=何も許可されていない）ときの 403。 */
internal fun accountNotProvisioned(subjectId: String): ProblemDetail =
    problem(
        status = HttpStatus.FORBIDDEN,
        code = "account-not-provisioned",
        title = "アカウントが登録されていません",
        detail = "認証は成功しましたが、この利用者（$subjectId）のアカウントが登録されていません。",
    )
