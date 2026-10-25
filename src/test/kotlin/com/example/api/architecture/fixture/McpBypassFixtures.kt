package com.example.api.architecture.fixture

import com.example.api.application.iam.world.WorldQueries
import com.example.api.domain.iam.model.account.AccountRepository
import com.example.api.domain.iam.model.account.SubjectId
import com.example.api.domain.shared.AccountId
import com.example.api.domain.shared.Actor
import com.example.api.domain.shared.WorldId

/**
 * MCP アダプタの迂回を検出するルール（#752）が違反を検出することを確認するためのフィクスチャ群。
 *
 * いずれも `McpActorFactory` の所有確認を迂回する書き方を意図的に表す。`McpAdapterRulesTest` 本体は `DoNotIncludeTests`
 * でテストソースを除外するため、本番ルールを汚染しない。
 */

/** 解決済みの `AccountId` と任意の `WorldId` から [Actor] を直接組むツール。 */
class ActorConstructingToolFixture {
    fun actorFor(accountId: AccountId, worldId: WorldId): Actor = Actor(accountId, worldId)
}

/** 所有確認済みの [Actor] の世界だけを差し替えるツール。 */
class ActorCopyingToolFixture {
    fun retarget(actor: Actor, worldId: WorldId): Actor = actor.copy(worldId = worldId)
}

/** 読み取りポート（`〜Queries`）を直接注入するツール。 */
class QueriesInjectingToolFixture(private val worlds: WorldQueries) {
    fun owns(accountId: AccountId, worldId: WorldId): Boolean =
        worlds.existsOwnedBy(accountId, worldId)
}

/** 書き込みポート（jMolecules の `@Repository`）を直接注入するツール。 */
class RepositoryInjectingToolFixture(private val accounts: AccountRepository) {
    fun accountIdOf(subjectId: String): AccountId? =
        accounts.findBySubjectId(SubjectId(subjectId))?.id
}
