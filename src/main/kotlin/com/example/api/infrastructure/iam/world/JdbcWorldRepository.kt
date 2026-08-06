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
import org.springframework.stereotype.Repository

/** ドメインポート [WorldRepository] の唯一の実装。Spring Data JDBC で永続化する（ADR-0027 / ADR-0030）。 */
@Repository
class JdbcWorldRepository(private val rows: WorldSpringDataRepository) : WorldRepository {

    override fun findOwnedBy(accountId: AccountId, id: WorldId): World? =
        rows.findByIdAndAccountId(id.value, accountId.value)?.toDomain()

    override fun save(world: World): Result<World, UpdateConflict> =
        try {
            Ok(rows.save(world.toRow()).toDomain())
        } catch (_: OptimisticLockingFailureException) {
            Err(UpdateConflict)
        }

    override fun deleteById(id: WorldId) = rows.deleteById(id.value)

    override fun existsByAccountId(accountId: AccountId): Boolean =
        rows.existsByAccountId(accountId.value)

    override fun existsByAccountIdAndName(accountId: AccountId, name: WorldName): Boolean =
        rows.existsByAccountIdAndName(accountId.value, name.value)

    private fun WorldRow.toDomain(): World =
        World.reconstitute(WorldId(id), AccountId(accountId), WorldName(name), version)

    private fun World.toRow(): WorldRow = WorldRow(id.value, accountId.value, name.value, version)
}
