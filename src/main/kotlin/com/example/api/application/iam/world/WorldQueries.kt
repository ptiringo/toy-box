package com.example.api.application.iam.world

import com.example.api.domain.shared.AccountId

/**
 * 世界一覧の読み取りポート（軽量 CQRS / L2 の Query 側。ADR-0031）。
 *
 * 書き込みポート [com.example.api.domain.iam.model.world.WorldRepository] とは別物の plain interface。
 * 実装（infrastructure）は集約・[com.example.api.infrastructure.iam.world.WorldRow] を経由せず `iam.world`
 * を直接読む。
 */
interface WorldQueries {
    /** そのアカウントが持つ世界を id 昇順（＝作成順。id は UUIDv7 相当）で返す（該当なしは空リスト）。 */
    fun findAllByAccountId(accountId: AccountId): List<WorldView>
}
