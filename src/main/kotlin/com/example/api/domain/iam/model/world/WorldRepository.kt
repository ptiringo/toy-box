package com.example.api.domain.iam.model.world

import com.example.api.domain.shared.AccountId
import com.example.api.domain.shared.WorldId
import com.github.michaelbull.result.Result
import org.jmolecules.ddd.annotation.Repository

/** [WorldRepository.saveIfNameAvailable] が返す失敗。 */
sealed interface WorldSaveFailure {
    /** 同一アカウント内に同名の世界が既にある。 */
    data object NameTaken : WorldSaveFailure

    /** 読み取り時点から競合した（version 不一致、または行の並行削除）。 */
    data object Conflict : WorldSaveFailure
}

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
     * 同一アカウント内で名前が空いていれば世界を永続化する（利用者が名前を指定する作成・改名の唯一の書き込み口）。
     *
     * 集約の [World.version] が null なら insert、非 null なら楽観ロック付き update になる。名前が既に使われていれば
     * [WorldSaveFailure.NameTaken]、読み取り時点から競合していれば [WorldSaveFailure.Conflict] を返す。どちらも呼び出し側は 409
     * に写す。
     *
     * **「名前が空いているか」の判定と保存を 1 手に閉じ込めているのが要点**（#739）。事前照会と保存を別々に呼ぶ形にすると、その 2 手のあいだに別のリクエストが同名を書き込む
     * TOCTOU が残り、負けた側は DB の `UNIQUE (account_id, name)` 違反＝未捕捉の例外（500）になる。 UNIQUE 違反は PostgreSQL
     * がトランザクションを abort 済みの状態で飛ばすため、捕まえても 409 には戻せない。実装は同一アカウント内の書き込みを 直列化してこの窓を閉じ、UNIQUE 制約は最後の
     * backstop として残す。
     *
     * 名前の判定は**自分自身を除く**ため、名前を変えない改名（no-op）はそのまま通る。
     */
    fun saveIfNameAvailable(world: World): Result<World, WorldSaveFailure>

    /**
     * 同一アカウント内に同名の世界が無ければ保存し、既にあればそれを返す（原子的な get-or-create）。
     *
     * この口は **DB の `UNIQUE (account_id, name)` を唯一の裁定者にする**（`INSERT ... ON CONFLICT DO NOTHING`
     * ＋衝突時の読み直し）ため、並行実行しても世界は増えず、UNIQUE 違反の例外も出ない。
     *
     * ただし**衝突を黙って吸収して先着を返す**ので、用途は衝突を利用者に見せる必要が無い経路（初回ログインで既定名の世界を用意する場合）に限る。 利用者が名前を指定する作成・改名は
     * 「同名が既にある」ことを 409 で返す必要があるため [saveIfNameAvailable] を使う。
     */
    fun saveIfAbsent(world: World): World

    /** 世界を削除する。配下のデータは DB の ON DELETE CASCADE で連鎖削除される。 */
    fun deleteById(id: WorldId)

    /** そのアカウントが世界を 1 つでも持っているかを判定する（初回ログインの判別用）。 */
    fun existsByAccountId(accountId: AccountId): Boolean
}
