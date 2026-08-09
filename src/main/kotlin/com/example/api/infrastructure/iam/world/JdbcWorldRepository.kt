package com.example.api.infrastructure.iam.world

import com.example.api.domain.iam.model.world.World
import com.example.api.domain.iam.model.world.WorldName
import com.example.api.domain.iam.model.world.WorldRepository
import com.example.api.domain.shared.AccountId
import com.example.api.domain.shared.UpdateConflict
import com.example.api.domain.shared.WorldId
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

/** ドメインポート [WorldRepository] の唯一の実装。Spring Data JDBC で永続化する（ADR-0027 / ADR-0030）。 */
@Repository
class JdbcWorldRepository(
    private val rows: WorldSpringDataRepository,
    private val jdbcClient: JdbcClient,
) : WorldRepository {

    override fun findOwnedBy(accountId: AccountId, id: WorldId): World? =
        rows.findByIdAndAccountId(id.value, accountId.value)?.toDomain()

    override fun save(world: World): Result<World, UpdateConflict> =
        try {
            Ok(rows.save(world.toRow()).toDomain())
        } catch (_: OptimisticLockingFailureException) {
            Err(UpdateConflict)
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

    override fun existsByAccountIdAndName(accountId: AccountId, name: WorldName): Boolean =
        rows.existsByAccountIdAndName(accountId.value, name.value)

    private fun WorldRow.toDomain(): World =
        World.reconstitute(WorldId(id), AccountId(accountId), WorldName(name), version)

    private fun World.toRow(): WorldRow = WorldRow(id.value, accountId.value, name.value, version)

    private companion object {
        /** Spring Data JDBC が insert 時に採番する初期 version と揃える（契約テストで縛っている）。 */
        const val INITIAL_VERSION = 0L
    }
}
