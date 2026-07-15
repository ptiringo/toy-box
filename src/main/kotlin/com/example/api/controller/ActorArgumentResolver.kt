package com.example.api.controller

import com.example.api.application.iam.account.ResolveActorQuery
import com.example.api.application.iam.account.ResolveActorUseCase
import com.example.api.application.iam.account.ResolveActorUseCaseError
import com.example.api.controller.problem.accountNotProvisioned
import com.example.api.domain.shared.Actor
import com.github.michaelbull.result.getOrElse
import org.springframework.core.MethodParameter
import org.springframework.http.HttpStatusCode
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.ErrorResponseException
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

/**
 * ハンドラの [Actor] 引数を、認証済み JWT の `sub` から組み立てて注入する。
 *
 * 認可の判断そのものは application 層のユースケースが行う（ADR-0064）。ここは「誰が」を DB から 引き当てて渡すだけで、権限の検査はしない。`account`
 * が未登録なら、認証は通っていても何も許可されて いない利用者なので 403 を投げる。
 */
@Component
class ActorArgumentResolver(private val resolveActor: ResolveActorUseCase) :
    HandlerMethodArgumentResolver {

    override fun supportsParameter(parameter: MethodParameter): Boolean =
        parameter.parameterType == Actor::class.java

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): Actor {
        val subject = SecurityContextHolder.getContext().authentication?.name.orEmpty()
        return resolveActor(ResolveActorQuery(subject)).getOrElse { error ->
            when (error) {
                is ResolveActorUseCaseError.AccountNotFound -> {
                    val problem = accountNotProvisioned(error.subjectId)
                    throw ErrorResponseException(
                        HttpStatusCode.valueOf(problem.status),
                        problem,
                        null,
                    )
                }
            }
        }
    }
}
