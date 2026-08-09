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
    /**
     * 指定のアカウントが所有する世界を ID で検索する。
     *
     * 所有していない世界・存在しない世界のいずれも null。**世界を触る操作は必ずこの口を通す**（所有を問わない `findById`
     * は用意しない。所有チェック漏れが「例外も出ず静かに混ざる」欠陥になるため、口を 1 つに寄せて SQL 側の `WHERE id = ? AND account_id = ?`
     * で強制する）。
     */
    fun findOwnedBy(accountId: AccountId, id: WorldId): World?

    /**
     * 世界を永続化する。
     *
     * 集約の [World.version] が null なら insert、非 null なら楽観ロック付き update になる。
     *
     * **同一アカウント内の名前重複（DB の `UNIQUE (account_id, name)`）はここでは検知しない**。UNIQUE 違反は PostgreSQL
     * 側でトランザクションを abort 済みの状態で例外として飛ぶため、呼び出し側は [existsByAccountIdAndName] で事前照会し、この `save`
     * を呼ぶ前に重複を弾くこと。
     */
    fun save(world: World): Result<World, UpdateConflict>

    /**
     * 同一アカウント内に同名の世界が無ければ保存し、既にあればそれを返す（原子的な get-or-create）。
     *
     * [existsByAccountIdAndName] による事前照会 → [save] は、その 2 手のあいだに別のリクエストが同名の世界を insert しうる TOCTOU
     * のレースを残す。この口は **DB の `UNIQUE (account_id, name)` を唯一の裁定者にする** （`INSERT ... ON CONFLICT DO
     * NOTHING` ＋衝突時の読み直し）ため、並行実行しても世界は増えず、UNIQUE 違反の 例外も出ない。
     *
     * 用途は**衝突を利用者に見せる必要が無い経路**（初回ログインで既定名の世界を用意する場合）に限る。 利用者が名前を指定する作成・改名は「同名が既にある」ことを 409
     * で返す必要があるため、事前照会と [save] を 使い続ける。
     */
    fun saveIfAbsent(world: World): World

    /** 世界を削除する。配下のデータは DB の ON DELETE CASCADE で連鎖削除される。 */
    fun deleteById(id: WorldId)

    /** そのアカウントが世界を 1 つでも持っているかを判定する（初回ログインの判別用）。 */
    fun existsByAccountId(accountId: AccountId): Boolean

    /** 同一アカウント内に同名の世界が既にあるかを判定する（作成・改名前の事前照会用）。 */
    fun existsByAccountIdAndName(accountId: AccountId, name: WorldName): Boolean
}
