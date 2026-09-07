package com.example.api.infrastructure.iam.account

import com.example.api.domain.iam.model.account.Account
import com.example.api.domain.iam.model.account.AccountRepository
import com.example.api.domain.iam.model.account.SubjectId
import com.example.api.domain.shared.AccountId
import com.example.api.domain.shared.UpdateConflict
import com.example.api.infrastructure.shared.lockRowIfVersionMatches
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

/** ドメインポート [AccountRepository] の唯一の実装。Spring Data JDBC で永続化する（ADR-0027 / ADR-0030）。 */
@Repository
class JdbcAccountRepository(
    private val rows: AccountSpringDataRepository,
    private val jdbcClient: JdbcClient,
) : AccountRepository {

    override fun findBySubjectId(subjectId: SubjectId): Account? =
        rows.findBySubjectId(subjectId.value)?.toDomain()

    /**
     * 版が一致するときだけ更新する（競合は例外にせず [UpdateConflict] で返す。#867）。
     *
     * insert（version が null）はロック不要。update のときだけ [lockRowIfVersionMatches] で行をロックして
     * 版を突き合わせ、一致した場合にのみ `save` を通す。詳細と、例外に頼れない理由はヘルパーの KDoc を参照。
     */
    override fun save(account: Account): Result<Account, UpdateConflict> {
        val version = account.version
        val id = account.id.value
        // version 不一致（並行更新）または行の並行削除。どちらも「読み取り時点から競合した」として扱う。
        if (version != null && !jdbcClient.lockRowIfVersionMatches(TABLE, id, version)) {
            return Err(UpdateConflict)
        }
        return Ok(rows.save(account.toRow()).toDomain())
    }

    /**
     * `ON CONFLICT DO NOTHING` で insert し、結果によらず DB の現状を読み直して返す。
     *
     * Spring Data JDBC の `save` は upsert を書けないため、ここだけ [JdbcClient] で SQL を直接書く。 **UNIQUE
     * 違反を例外にしない**ことが要点で、これにより PostgreSQL がトランザクションを abort せず、 同一トランザクション内で読み直せる（例外を捕まえる方式では abort
     * 済みのため別トランザクション境界が要る）。
     *
     * 衝突しなかった場合も読み直すのは、採番済みの `version` を含む DB の実状態を 1 か所のマッピング （[findBySubjectId]）で返すため。往復が 1
     * 回増えるが、この口を通るのは初回ログインだけで割に合う。
     */
    override fun saveIfAbsent(account: Account): Account {
        val subjectId = account.subjectId
        jdbcClient
            .sql(
                "INSERT INTO iam.account (id, subject_id, version) " +
                    "VALUES (:id, :subjectId, :version) " +
                    "ON CONFLICT (subject_id) DO NOTHING"
            )
            .param("id", account.id.value)
            .param("subjectId", subjectId.value)
            .param("version", INITIAL_VERSION)
            .update()
        // insert したなら自分の行、衝突したなら先着の行。DO NOTHING は先行トランザクションの
        // 確定を待ってから効くため、この時点で行は必ず存在する（READ COMMITTED 前提）。
        return checkNotNull(findBySubjectId(subjectId)) { "insert 直後に引けない: ${subjectId.value}" }
    }

    private fun AccountRow.toDomain(): Account =
        Account.reconstitute(AccountId(id), SubjectId(subjectId), version)

    private fun Account.toRow(): AccountRow = AccountRow(id.value, subjectId.value, version)

    private companion object {
        /** 楽観ロックの行ロックを掛ける対象（[lockRowIfVersionMatches] に渡す完全修飾名）。 */
        const val TABLE = "iam.account"

        /**
         * Spring Data JDBC が insert 時に採番する初期 version と揃える。ずれると経路によって楽観ロックの前提が
         * 食い違うため、契約テスト（`saveIfAbsent の初回保存は save と同じ version を採番する`）で縛っている。
         */
        const val INITIAL_VERSION = 0L
    }
}
