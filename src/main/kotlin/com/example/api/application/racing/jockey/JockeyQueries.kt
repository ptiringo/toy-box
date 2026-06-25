package com.example.api.application.racing.jockey

import com.example.api.domain.racing.model.jockey.JockeyId

/**
 * ジョッキーの読み取りポート（軽量 CQRS（L2）の Query 側。ADR-0031）。
 *
 * 書き込みポート [com.example.api.domain.racing.model.jockey.JockeyRepository] とは**別物として割る**。
 * 同じストアを読んでも、経路（write=集約復元 / read=View 直組み）とモデルを分離するのが L2 の価値であり、 「同じテーブルなら write ポートに finder
 * を生やせばよい」という誘惑には乗らない。
 *
 * 読み取りポートは集約のライフサイクル（生成・状態遷移・整合性境界）を持たないため、書き込みポートが付ける jMolecules `@Repository`（DDD
 * ビルディングブロック）は**付けない**。読みモデル・クエリポートはドメインを 汚さず application 層に置き、実装は infrastructure 層に置く（オニオン依存方向は
 * write と対称に維持）。
 */
interface JockeyQueries {
    /** ID でジョッキービューを引く。存在しなければ null（単純 lookup は Result を強制しない。error-handling.md）。 */
    fun findById(id: JockeyId): JockeyView?
}
