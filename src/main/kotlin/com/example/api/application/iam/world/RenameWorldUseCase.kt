package com.example.api.application.iam.world

import com.example.api.domain.iam.model.world.WorldRepository
import com.example.api.domain.iam.model.world.WorldSaveFailure
import com.example.api.domain.shared.AccountId
import com.example.api.domain.shared.Command
import com.example.api.domain.shared.WorldId
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.andThen
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapError
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 世界改名の入力コマンド。
 *
 * @property worldId 改名対象の世界のID
 * @property name 新しい名前
 */
data class RenameWorldCommand(val worldId: WorldId, val name: String)

/**
 * 自分の世界の名前を変えるユースケース。
 *
 * 名前の重複は事前照会せず、[WorldRepository.saveIfNameAvailable] に判定ごと委ねる（#739）。事前照会と保存が別々の呼び出しに分かれていると、その 2
 * 手のあいだに別のリクエストが同名を書き込む TOCTOU が残り、負けた側は DB の `UNIQUE (account_id, name)` 違反＝未捕捉の例外（500）になるため。
 * 現在の名前へ改名する no-op は、ポート側が重複判定から自分自身を除くのでそのまま通る。
 */
@Service
class RenameWorldUseCase(private val worlds: WorldRepository) {

    @Transactional
    operator fun invoke(
        accountId: AccountId,
        command: Command<RenameWorldCommand>,
    ): Result<WorldView, WorldMutationError> =
        worlds
            .findOwnedByOrNotFound(accountId, command.payload.worldId)
            .andThen { world ->
                world.rename(command.payload.name).mapError { WorldMutationError.InvalidName(it) }
            }
            .andThen { renamed -> worlds.saveIfNameAvailable(renamed).mapError { it.toError() } }
            .map { WorldView(it.id.value, it.name.value) }

    /** 保存の失敗はいずれも「書き換えられなかった＝競合」として 1 つに畳む（呼び出し側はどちらも 409 に描画する）。 */
    private fun WorldSaveFailure.toError(): WorldMutationError =
        when (this) {
            WorldSaveFailure.NameTaken,
            WorldSaveFailure.Conflict -> WorldMutationError.Conflict
        }
}
