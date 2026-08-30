package com.example.api.infrastructure.iam.world

import com.example.api.domain.iam.model.world.World
import com.example.api.domain.iam.model.world.WorldName
import com.example.api.domain.iam.model.world.WorldRepository
import com.example.api.domain.iam.model.world.WorldSaveFailure
import com.example.api.domain.shared.AccountId
import com.example.api.domain.shared.WorldId
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/** ドメインポート [WorldRepository] の唯一の実装。Spring Data JDBC で永続化する（ADR-0027 / ADR-0030）。 */
@Repository
class JdbcWorldRepository(
    private val rows: WorldSpringDataRepository,
    private val jdbcClient: JdbcClient,
) : WorldRepository {

    override fun findOwnedBy(accountId: AccountId, id: WorldId): World? =
        rows.findByIdAndAccountId(id.value, accountId.value)?.toDomain()

    /**
     * 所有アカウントの行をロックしてから名前の重複を判定し、空いていれば保存する（#739）。
     *
     * `UNIQUE (account_id, name)` 違反を**起こさせない**のが要点。UNIQUE 違反は PostgreSQL がトランザクションを abort 済みの状態で
     * 例外として飛ばすため、捕まえても `Err` に写して 409 を返すことはできない（同一トランザクション内で以降の SQL が一切通らない）。
     *
     * 判定と保存のあいだに他のリクエストを割り込ませないため、`iam.account` の自分の行を `FOR UPDATE` でロックして**同一アカウント内の世界の書き込みを
     * 直列化する**。ロックの粒度はアカウント単位なので、直列化されるのは同じプレイヤーの世界の作成・改名だけで、他のプレイヤーは待たされない。ロック順序は `account` →
     * `world` で `ProvisionMeUseCase`（アカウント作成 → 世界作成）と一致するためデッドロックしない。
     *
     * ロックはトランザクションの終了まで保持されるので、**呼び出し側のトランザクション内で呼ばれること**が前提になる。トランザクションが無いと `FOR UPDATE`
     * は即座に解放されて直列化が成立しない（＝並行競合が 500 に戻る）が、それは無症状で起きるため [Propagation.MANDATORY]
     * で「トランザクション必須」を宣言し、誤用を例外として顕在化させる。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    override fun saveIfNameAvailable(world: World): Result<World, WorldSaveFailure> {
        lockOwner(world.accountId)
        // 自分自身は判定から除く（名前を変えない改名を「自分と衝突している」と誤判定させないため）。
        val nameTaken =
            rows.existsByAccountIdAndNameAndIdNot(
                world.accountId.value,
                world.name.value,
                world.id.value,
            )
        if (nameTaken) {
            return Err(WorldSaveFailure.NameTaken)
        }
        val version = world.version
        return if (version == null) Ok(insert(world)) else update(world, version)
    }

    /** 同一アカウント内の世界の書き込みを直列化する（名前の判定と保存のあいだに割り込ませないため）。 */
    private fun lockOwner(accountId: AccountId) {
        jdbcClient
            .sql("SELECT 1 FROM iam.account WHERE id = :accountId FOR UPDATE")
            .param("accountId", accountId.value)
            .query(Int::class.java)
            .optional()
    }

    private fun insert(world: World): World {
        jdbcClient
            .sql(
                "INSERT INTO iam.world (id, account_id, name, version) " +
                    "VALUES (:id, :accountId, :name, :version)"
            )
            .param("id", world.id.value)
            .param("accountId", world.accountId.value)
            .param("name", world.name.value)
            .param("version", INITIAL_VERSION)
            .update()
        return world.withVersion(INITIAL_VERSION)
    }

    /**
     * 版を進めながら名前を書き換え、**更新行数で**楽観ロックの競合を判定する。
     *
     * Spring Data JDBC の `save` は競合を `OptimisticLockingFailureException` で知らせるが、その口は使えない。 リポジトリ自身が
     * `@Transactional` を持つため、参加中のトランザクションで例外が起きた時点で global rollback-only が マークされ、こちらが捕まえて `Err`
     * に写しても**外側のコミットが `UnexpectedRollbackException` になる**（＝ 409 のつもりが 500）。UNIQUE 違反を `ON CONFLICT
     * DO NOTHING` で例外にしないのと同じ理由で、こちらも 例外を起こさせずに結果だけを見る。
     *
     * `account_id` は更新対象に含めない（世界の所有者は変わらない）。
     */
    private fun update(world: World, version: Long): Result<World, WorldSaveFailure> {
        val next = version + 1
        val updated =
            jdbcClient
                .sql(
                    "UPDATE iam.world SET name = :name, version = :next " +
                        "WHERE id = :id AND version = :version"
                )
                .param("name", world.name.value)
                .param("next", next)
                .param("id", world.id.value)
                .param("version", version)
                .update()
        // version 不一致（並行更新）または行の並行削除。どちらも「読み取り時点から競合した」として扱う。
        return if (updated == 0) Err(WorldSaveFailure.Conflict) else Ok(world.withVersion(next))
    }

    /**
     * `ON CONFLICT DO NOTHING` で insert し、結果によらず DB の現状を読み直して返す。
     *
     * 設計意図は `JdbcAccountRepository.saveIfAbsent` と同じで、UNIQUE 違反を例外にせず PostgreSQL の トランザクション abort
     * を避ける（詳細はそちらの KDoc）。
     */
    override fun saveIfAbsent(world: World): World {
        val accountId = world.accountId
        val name = world.name
        jdbcClient
            .sql(
                "INSERT INTO iam.world (id, account_id, name, version) " +
                    "VALUES (:id, :accountId, :name, :version) " +
                    "ON CONFLICT (account_id, name) DO NOTHING"
            )
            .param("id", world.id.value)
            .param("accountId", accountId.value)
            .param("name", name.value)
            .param("version", INITIAL_VERSION)
            .update()
        return checkNotNull(findByName(accountId, name)) { "insert 直後に引けない: ${name.value}" }
    }

    /** 同一アカウント内の同名の世界を引く（[saveIfAbsent] が衝突後に先着を読み直すための口）。 */
    private fun findByName(accountId: AccountId, name: WorldName): World? =
        rows.findByAccountIdAndName(accountId.value, name.value)?.toDomain()

    override fun deleteById(id: WorldId) = rows.deleteById(id.value)

    override fun existsByAccountId(accountId: AccountId): Boolean =
        rows.existsByAccountId(accountId.value)

    private fun WorldRow.toDomain(): World =
        World.reconstitute(WorldId(id), AccountId(accountId), WorldName(name), version)

    /** DB が確定した版を持つ集約として返す（呼び出し側が続けて更新できるようにするため）。 */
    private fun World.withVersion(version: Long): World =
        World.reconstitute(id, accountId, name, version)

    private companion object {
        /**
         * insert 時に採番する初期 version。
         *
         * 世界の insert 経路は 2 つ（[saveIfNameAvailable] と [saveIfAbsent]）あり、どちらも upsert / 更新行数の判定のために
         * SQL を手書きするため、初期値がずれると経路によって以後の楽観ロックの前提が食い違う。契約テストで両者が揃うことを縛っている。
         */
        const val INITIAL_VERSION = 0L
    }
}
