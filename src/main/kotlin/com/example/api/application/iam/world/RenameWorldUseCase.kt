package com.example.api.application.iam.world

import com.example.api.domain.iam.model.world.World
import com.example.api.domain.iam.model.world.WorldRepository
import com.example.api.domain.shared.AccountId
import com.example.api.domain.shared.Command
import com.example.api.domain.shared.UpdateConflict
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.andThen
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapError
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 世界改名の入力コマンド。
 *
 * @property worldId 改名対象の世界の生 UUID
 * @property name 新しい名前
 */
data class RenameWorldCommand(val worldId: UUID, val name: String)

/**
 * 自分の世界の名前を変えるユースケース。
 *
 * 同名の世界への改名は、DB の `UNIQUE (account_id, name)` に検知を委ねず [WorldRepository.existsByAccountIdAndName]
 * で事前照会して弾く（I-1: UNIQUE 制約違反はトランザクション abort を伴うため未捕捉の例外＝500 になり、 `Conflict`＝409 に届かない）。現在の名前へ改名する
 * no-op は「自分自身との重複」なので事前照会をスキップする。
 */
@Service
class RenameWorldUseCase(private val worlds: WorldRepository) {

    @Transactional
    operator fun invoke(
        accountId: AccountId,
        command: Command<RenameWorldCommand>,
    ): Result<WorldView, WorldMutationError> =
        worlds
            .findOwnedBy(accountId, command.payload.worldId)
            .andThen { world ->
                world
                    .rename(command.payload.name)
                    .mapError { WorldMutationError.InvalidName(it) }
                    .andThen { renamed -> checkNameAvailable(accountId, world, renamed) }
            }
            .andThen { renamed ->
                worlds.save(renamed).mapError { _: UpdateConflict -> WorldMutationError.Conflict }
            }
            .map { WorldView(it.id.value, it.name.value) }

    /** 名前が変わらない no-op は自分自身と衝突するため除外し、変わる場合のみ重複を事前照会する。 */
    private fun checkNameAvailable(
        accountId: AccountId,
        original: World,
        renamed: World,
    ): Result<World, WorldMutationError> {
        val nameChanged = renamed.name != original.name
        val nameTaken = nameChanged && worlds.existsByAccountIdAndName(accountId, renamed.name)
        return if (nameTaken) Err(WorldMutationError.Conflict) else Ok(renamed)
    }
}
