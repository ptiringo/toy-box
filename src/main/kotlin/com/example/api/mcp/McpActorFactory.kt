package com.example.api.mcp

import com.example.api.application.iam.world.WorldQueries
import com.example.api.domain.iam.model.account.AccountRepository
import com.example.api.domain.iam.model.account.SubjectId
import com.example.api.domain.shared.AccountId
import com.example.api.domain.shared.Actor
import com.example.api.domain.shared.WorldId
import java.util.UUID
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * MCP ツールが使う [Actor]（アカウント ＋ 操作対象の世界）を組み立てる（#712 / ADR-0067）。
 *
 * REST の [com.example.api.controller.ActorArgumentResolver] と同じ 3 手を踏む。違うのは入力の出所だけで、 JWT の `sub`
 * が設定（[McpProperties.subjectId]）に、パスの `{worldId}` がツール引数に替わる。adapter 同士の 参照は規約で禁止されているためコードは共有せず、この
 * 3 手を MCP 側に持つ。
 *
 * **`accountId` を引数で受けない**ことが要。呼び出し側が指定できるのは `worldId` だけで、所有していない世界は [WorldQueries.existsOwnedBy]
 * が false を返して弾かれる。したがって認証の無い MCP でもテナント分離は破れない （#704 が「worldId を引数に足すのは穴」とした懸念は、`accountId`
 * が設定で固定されることで消える）。
 */
@Profile("local")
@Component
class McpActorFactory(
    private val properties: McpProperties,
    private val accounts: AccountRepository,
    private val worlds: WorldQueries,
) {

    /**
     * 設定の subject から [AccountId] を解決する。世界を要しないツール（世界の一覧）はこれだけで足りる。
     *
     * 失敗はいずれも配線・設定の誤りであり、業務エラーではないため落とす（fail-loud）。
     */
    fun resolveAccountId(): AccountId {
        val subjectId = properties.subjectId
        check(subjectId.isNotBlank()) {
            "toy-box.mcp.subject-id が未設定です（MCP_SUBJECT_ID 環境変数を渡してください）"
        }
        val account =
            accounts.findBySubjectId(SubjectId(subjectId))
                ?: error("subject=$subjectId のアカウントが未登録です（先に POST /api/me:provision を実行してください）")
        return account.id
    }

    /**
     * 設定の subject とツール引数の `worldId` から [Actor] を組む。
     *
     * 所有していない世界・存在しない世界のいずれも [NoSuchElementException] にする。両者を区別しないのは意図で、
     * 区別すると「存在するが、あなたのものではない」が漏れる（ADR-0067）。
     */
    fun actorFor(worldId: String): Actor {
        val accountId = resolveAccountId()
        val id = WorldId(UUID.fromString(worldId))
        if (!worlds.existsOwnedBy(accountId, id)) {
            throw NoSuchElementException("world not found: $worldId")
        }
        return Actor(accountId = accountId, worldId = id)
    }
}
