package com.example.api.controller

import com.example.api.application.iam.world.WorldQueries
import com.example.api.domain.iam.model.account.AccountRepository
import com.example.api.domain.iam.model.account.SubjectId
import com.example.api.domain.shared.Actor
import com.example.api.domain.shared.WorldId
import java.util.UUID
import org.springframework.core.MethodParameter
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import org.springframework.web.servlet.HandlerMapping

/**
 * パスの `{worldId}` が指す世界が存在しないか、リクエスト元のアカウントが所有していない。
 *
 * 両者を区別せず同じ例外にするのは意図（ADR-0067）。403 は「存在するが、あなたのものではない」と漏らすため、 箱庭では区別する理由が無い。
 */
class WorldNotFoundException(val worldId: UUID) :
    RuntimeException("worldId=$worldId の世界は存在しないか、所有していません")

/**
 * 検証済み JWT の `sub` とパスの `{worldId}` から [Actor]（アカウント ＋ 操作対象の世界）を解決してハンドラへ注入する。
 *
 * この API の唯一の認可判断「この世界はあなたのものか」はここで 1 度だけ行う（ADR-0067）。以降のユースケースが 行うのは `WHERE world_id = ?`
 * というデータのスコープであって、認可の判断ではない。
 *
 * 認証（トークンの検証）は `SecurityConfig` のフィルタが済ませている前提。3 つの失敗を区別する。
 * - 認証が無い / JWT でない、パスに `{worldId}` が無い → 配線ミスなので 403 / 404 に化けさせず落とす（fail-loud）
 * - `sub` に対応するアカウントが未登録 → 403 `account-not-provisioned`
 * - 世界を所有していない / 世界が存在しない → 404 `world-not-found`（両者を区別しない。403 は「存在するが、 あなたのものではない」と漏らすため）
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
    ): Actor = resolveActor(webRequest)

    /** テストから直接叩けるように解決本体を切り出す。 */
    fun resolveActor(webRequest: NativeWebRequest): Actor {
        val authentication = SecurityContextHolder.getContext().authentication
        check(authentication is JwtAuthenticationToken) {
            "認証済み JWT が SecurityContext にありません（フィルタ設定の誤りです）"
        }
        val subject = authentication.token.subject
        check(!subject.isNullOrBlank()) { "検証済み JWT に sub がありません" }

        val account =
            accounts.findBySubjectId(SubjectId(subject))
                ?: throw AccountNotProvisionedException(subject)

        val worldId = WorldId(pathWorldId(webRequest))
        if (!worlds.existsOwnedBy(account.id, worldId)) {
            throw WorldNotFoundException(worldId.value)
        }
        return Actor(accountId = account.id, worldId = worldId)
    }

    /**
     * パステンプレートの `{worldId}` を取り出す。
     *
     * 取れないのは `@CurrentActor` を世界の下に無いエンドポイントで使ったということで、配線ミス。404 に化けさせると 原因が隠れるため落とす。UUID
     * として不正な値はハンドラ引数 `@PathVariable worldId: UUID` の型変換が 400 で弾くので、ここには届かない。
     */
    private fun pathWorldId(webRequest: NativeWebRequest): UUID {
        @Suppress("UNCHECKED_CAST")
        val variables =
            webRequest.getAttribute(
                HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
                RequestAttributes.SCOPE_REQUEST,
            ) as? Map<String, String>
        val raw = variables?.get("worldId")
        check(raw != null) { "パスに {worldId} がありません（@CurrentActor を世界スコープ外で使っています）" }
        return UUID.fromString(raw)
    }
}
