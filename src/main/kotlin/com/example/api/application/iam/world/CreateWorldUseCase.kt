package com.example.api.application.iam.world

import com.example.api.domain.iam.model.world.World
import com.example.api.domain.iam.model.world.WorldNameValidationError
import com.example.api.domain.iam.model.world.WorldRepository
import com.example.api.domain.iam.model.world.WorldSaveFailure
import com.example.api.domain.shared.AccountId
import com.example.api.domain.shared.Command
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

    /** 同一アカウント内に同名の世界が既にある。 */
    data object Conflict : CreateWorldError
}

/**
 * 自分の世界を新しく作るユースケース。
 *
 * 名前の重複は事前照会せず、[WorldRepository.saveIfNameAvailable] に判定ごと委ねる（#739）。事前照会と保存が別々の呼び出しに分かれていると、その 2
 * 手のあいだに別のリクエストが同名を書き込む TOCTOU が残り、負けた側は DB の `UNIQUE (account_id, name)` 違反＝未捕捉の例外（500）になるため。
 */
@Service
class CreateWorldUseCase(private val worlds: WorldRepository) {

    @Transactional
    operator fun invoke(
        accountId: AccountId,
        command: Command<CreateWorldCommand>,
    ): Result<WorldView, CreateWorldError> =
        World.create(accountId, command.payload.name)
            .mapError { CreateWorldError.InvalidName(it) }
            .andThen { world -> worlds.saveIfNameAvailable(world).mapError { it.toError() } }
            .map { WorldView(it.id.value, it.name.value) }

    /** 保存の失敗はいずれも「作れなかった＝競合」として 1 つに畳む（呼び出し側はどちらも 409 に描画する）。 */
    private fun WorldSaveFailure.toError(): CreateWorldError =
        when (this) {
            WorldSaveFailure.NameTaken,
            WorldSaveFailure.Conflict -> CreateWorldError.Conflict
        }
}
