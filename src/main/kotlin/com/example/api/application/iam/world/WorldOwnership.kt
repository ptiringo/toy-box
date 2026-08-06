// このファイルは「世界を触る操作」に関わる 2 つの宣言（失敗型 WorldMutationError と、所有付き lookup の
// findOwnedBy）を意図的に同居させている（KDoc の通り、両者は対で使う一体の口）。ファイル名はどちらの宣言名とも
// 一致しないため detekt の MatchingDeclarationName を抑止する。
@file:Suppress("MatchingDeclarationName")

package com.example.api.application.iam.world

import com.example.api.domain.iam.model.world.World
import com.example.api.domain.iam.model.world.WorldNameValidationError
import com.example.api.domain.iam.model.world.WorldRepository
import com.example.api.domain.shared.AccountId
import com.example.api.domain.shared.WorldId
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import java.util.UUID

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

/**
 * **世界を触る操作（改名・削除等）はここを通す。**
 *
 * ポートの [WorldRepository.findOwnedBy] は所有していない世界に対して null を返すだけだが、ユースケース側は 「他人の世界も存在しない世界も区別せず
 * [WorldMutationError.NotFound]」という語彙で失敗を表したい。この ギャップを埋める薄いラッパーで、`Result` を返さない生の `findOwnedBy`
 * を直接呼ぶ代わりに常にこちらを使う。
 */
internal fun WorldRepository.findOwnedBy(
    accountId: AccountId,
    worldId: UUID,
): Result<World, WorldMutationError> {
    val world = findOwnedBy(accountId, WorldId(worldId))
    return if (world != null) {
        Ok(world)
    } else {
        Err(WorldMutationError.NotFound(worldId))
    }
}
