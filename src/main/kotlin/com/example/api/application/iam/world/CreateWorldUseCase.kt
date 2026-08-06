package com.example.api.application.iam.world

import com.example.api.domain.iam.model.world.World
import com.example.api.domain.iam.model.world.WorldNameValidationError
import com.example.api.domain.iam.model.world.WorldRepository
import com.example.api.domain.shared.AccountId
import com.example.api.domain.shared.Command
import com.example.api.domain.shared.UpdateConflict
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.andThen
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapError
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 世界作成の入力コマンド。
 *
 * @property name プレイヤーが付ける世界の名前
 */
data class CreateWorldCommand(val name: String)

/** 世界作成時に発生しうる失敗。 */
sealed interface CreateWorldError {
    /** 名前が不変条件を満たさない。 */
    data class InvalidName(val cause: WorldNameValidationError) : CreateWorldError

    /** 同一アカウント内に同名の世界が既にある（DB の UNIQUE 制約に弾かれた）。 */
    data object Conflict : CreateWorldError
}

/** 自分の世界を新しく作るユースケース。 */
@Service
class CreateWorldUseCase(private val worlds: WorldRepository) {

    @Transactional
    operator fun invoke(
        accountId: AccountId,
        command: Command<CreateWorldCommand>,
    ): Result<WorldView, CreateWorldError> =
        World.create(accountId, command.payload.name)
            .mapError { CreateWorldError.InvalidName(it) }
            .andThen { world ->
                worlds.save(world).mapError { _: UpdateConflict -> CreateWorldError.Conflict }
            }
            .map { WorldView(it.id.value, it.name.value) }
}
