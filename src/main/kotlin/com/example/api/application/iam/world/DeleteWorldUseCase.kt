package com.example.api.application.iam.world

import com.example.api.domain.iam.model.world.WorldRepository
import com.example.api.domain.shared.AccountId
import com.example.api.domain.shared.Command
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 世界削除の入力コマンド。
 *
 * @property worldId 削除対象の世界の生 UUID
 */
data class DeleteWorldCommand(val worldId: UUID)

/**
 * 自分の世界を削除するユースケース。
 *
 * 配下のドメインデータは DB の ON DELETE CASCADE で連鎖削除される（世界スコープ化の後は全集約が対象になる）。
 */
@Service
class DeleteWorldUseCase(private val worlds: WorldRepository) {

    @Transactional
    operator fun invoke(
        accountId: AccountId,
        command: Command<DeleteWorldCommand>,
    ): Result<Unit, WorldMutationError> =
        worlds.findOwnedBy(accountId, command.payload.worldId).map { worlds.deleteById(it.id) }
}
