package com.example.api.application.iam.world

import com.example.api.domain.shared.AccountId
import org.springframework.stereotype.Service

/**
 * 世界一覧クエリの入力。読み取り系なので `Command` 封筒は使わない（ADR-0031）。
 *
 * @property accountId 一覧を引くアカウントのID
 */
data class ListWorldsQuery(val accountId: AccountId)

/**
 * 自分の世界を一覧するユースケース（軽量 CQRS の読み取り側。ADR-0031）。
 *
 * コレクション照会のため失敗バリアントは設けない（該当なし＝空リスト）。
 */
@Service
class ListWorldsUseCase(private val worldQueries: WorldQueries) {
    operator fun invoke(query: ListWorldsQuery): List<WorldView> =
        worldQueries.findAllByAccountId(query.accountId)
}
