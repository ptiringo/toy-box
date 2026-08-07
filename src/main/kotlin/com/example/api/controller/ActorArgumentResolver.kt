package com.example.api.controller

import com.example.api.application.iam.world.WorldQueries
import com.example.api.domain.iam.model.account.AccountRepository
import com.example.api.domain.iam.model.account.SubjectId
import com.example.api.domain.shared.Actor
import com.example.api.domain.shared.WorldId
import org.springframework.core.MethodParameter
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

/**
 * 検証済み JWT の `sub` から [Actor]（アカウント ＋ 操作対象の世界）を解決してハンドラへ注入する。
 *
 * **世界の解決は暫定実装**（#704）。いまはそのアカウントが持つ世界の先頭（id 昇順 ＝ `:provision` が作る 「はじまりの世界」）を使う。#705 でパスの
 * `{worldId}` を受け取り、そのアカウントが所有しているかを確認する形 （所有していなければ 404）へ差し替える。差し替わるのは [resolveActor]
 * だけで、ユースケース以下のシグネチャは変わらない。
 *
 * 認証（トークンの検証）は `SecurityConfig` のフィルタが済ませている前提。認証が無い / JWT でない場合や、 世界が 1 つも無い場合は 403
 * に化けさせず落とす（fail-loud）。`:provision` が必ず世界を作るため、世界が 無いのは配線ミスであり、それを「未登録なので 403」と見せると原因が隠れるから。
 */
@Component
class ActorArgumentResolver(
    private val accounts: AccountRepository,
    private val worlds: WorldQueries,
) : HandlerMethodArgumentResolver {

    /**
     * `@CurrentActor actor: Actor` を持つハンドラ引数を判定する。
     *
     * [Actor] は `data class`（value class ではない）ため、`AccountId` のような JVM シグネチャ上の型消去は起きず、素直に型一致で判定できる。
     */
    override fun supportsParameter(parameter: MethodParameter): Boolean =
        parameter.hasParameterAnnotation(CurrentActor::class.java) &&
            parameter.parameterType == Actor::class.java

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): Actor = resolveActor()

    /** テストから直接叩けるように解決本体を切り出す。 */
    fun resolveActor(): Actor {
        val authentication = SecurityContextHolder.getContext().authentication
        check(authentication is JwtAuthenticationToken) {
            "認証済み JWT が SecurityContext にありません（フィルタ設定の誤りです）"
        }
        val subject = authentication.token.subject
        check(!subject.isNullOrBlank()) { "検証済み JWT に sub がありません" }

        val account =
            accounts.findBySubjectId(SubjectId(subject))
                ?: throw AccountNotProvisionedException(subject)
        val world =
            worlds.findAllByAccountId(account.id).firstOrNull()
                ?: error("アカウント ${account.id.value} に世界がありません（:provision の配線ミスです）")

        return Actor(accountId = account.id, worldId = WorldId(world.id))
    }
}
