package com.example.api.controller

import com.example.api.domain.iam.model.account.AccountRepository
import com.example.api.domain.iam.model.account.SubjectId
import com.example.api.domain.shared.AccountId
import org.springframework.core.MethodParameter
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

/** 検証済みトークンの subject に対応する [com.example.api.domain.iam.model.account.Account] が未登録だった。 */
class AccountNotProvisionedException(val subjectId: String) :
    RuntimeException("subject=$subjectId のアカウントが未登録です")

/**
 * 検証済み JWT の `sub` から [AccountId] を解決してハンドラへ注入する。
 *
 * 認証（トークンの検証）は `SecurityConfig` のフィルタが済ませている前提。ここが行うのは 「その subject に対応するアカウントがこの API
 * に登録されているか」の引き当てだけ。
 *
 * 認証が無い / JWT でない場合は 403 に化けさせず `IllegalStateException` で落とす（fail-loud）。
 * 認証済みでなければハンドラに到達しないはずで、到達したのなら配線ミスだから。空の `sub` で DB を 引いて「未登録なので 403」と見せると、原因が隠れる。
 */
@Component
class CurrentAccountArgumentResolver(private val accounts: AccountRepository) :
    HandlerMethodArgumentResolver {

    override fun supportsParameter(parameter: MethodParameter): Boolean =
        parameter.hasParameterAnnotation(CurrentAccount::class.java) &&
            parameter.parameterType == AccountId::class.java

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): AccountId = resolveAccountId()

    /** テストから直接叩けるように解決本体を切り出す。 */
    fun resolveAccountId(): AccountId {
        val authentication = SecurityContextHolder.getContext().authentication
        check(authentication is JwtAuthenticationToken) {
            "認証済み JWT が SecurityContext にありません（フィルタ設定の誤りです）"
        }
        val subject = authentication.token.subject
        check(!subject.isNullOrBlank()) { "検証済み JWT に sub がありません" }

        return accounts.findBySubjectId(SubjectId(subject))?.id
            ?: throw AccountNotProvisionedException(subject)
    }
}
