package com.example.api.domain.iam.model.world

import com.example.api.domain.shared.AccountId
import com.example.api.domain.shared.UpdateConflict
import com.example.api.domain.shared.WorldId
import com.github.michaelbull.result.Result
import org.jmolecules.ddd.annotation.Repository

/**
 * [World] の永続化を担うポート。
 *
 * 一覧の取得はここには置かない。読み取りは軽量 CQRS の Query 側
 * （[com.example.api.application.iam.world.WorldQueries]）が担う（ADR-0031）。
 */
@Repository
interface WorldRepository {
    /** 世界IDで検索する。存在しなければ null。 */
    fun findById(id: WorldId): World?

    /**
     * 世界を永続化する。
     *
     * 集約の [World.version] が null なら insert、非 null なら楽観ロック付き update になる。
     */
    fun save(world: World): Result<World, UpdateConflict>

    /** 世界を削除する。配下のデータは DB の ON DELETE CASCADE で連鎖削除される。 */
    fun deleteById(id: WorldId)

    /** そのアカウントが世界を 1 つでも持っているかを判定する（初回ログインの判別用）。 */
    fun existsByAccountId(accountId: AccountId): Boolean
}
