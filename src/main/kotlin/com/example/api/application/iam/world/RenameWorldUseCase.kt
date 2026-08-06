package com.example.api.application.iam.world

import com.example.api.domain.iam.model.world.World
import com.example.api.domain.iam.model.world.WorldNameValidationError
import com.example.api.domain.iam.model.world.WorldRepository
import com.example.api.domain.shared.AccountId
import com.example.api.domain.shared.Command
import com.example.api.domain.shared.UpdateConflict
import com.example.api.domain.shared.WorldId
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

/** 既存の世界を書き換える操作で発生しうる失敗。 */
sealed interface WorldMutationError {
    /**
     * 対象の世界が存在しない。
     *
     * **他人の世界を指した場合もこれを返す**（403 ではない）。403 は「存在するが、あなたのものではない」と 漏らしてしまい、箱庭では他人の世界の存在を知らせる理由が無いため。
     */
    data class NotFound(val worldId: UUID) : WorldMutationError

    /** 新しい名前が不変条件を満たさない。 */
    data class InvalidName(val cause: WorldNameValidationError) : WorldMutationError

    /** 同名の世界が既にある、または並行更新と競合した。 */
    data object Conflict : WorldMutationError
}

/** 自分の世界の名前を変えるユースケース。 */
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
                world.rename(command.payload.name).mapError { WorldMutationError.InvalidName(it) }
            }
            .andThen { renamed ->
                worlds.save(renamed).mapError { _: UpdateConflict -> WorldMutationError.Conflict }
            }
            .map { WorldView(it.id.value, it.name.value) }
}

/** 指定のアカウントが所有する世界を引く。所有していない・存在しないのいずれも [WorldMutationError.NotFound] として区別せず返す。 */
internal fun WorldRepository.findOwnedBy(
    accountId: AccountId,
    worldId: UUID,
): Result<World, WorldMutationError> {
    val world = findById(WorldId(worldId))
    return if (world != null && world.isOwnedBy(accountId)) {
        Ok(world)
    } else {
        Err(WorldMutationError.NotFound(worldId))
    }
}
