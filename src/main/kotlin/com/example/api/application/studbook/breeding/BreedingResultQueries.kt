package com.example.api.application.studbook.breeding

import com.example.api.domain.shared.WorldId
import com.example.api.domain.studbook.model.breeding.BreedingResultId

/**
 * 繁殖成績の読み取りポート（軽量 CQRS / L2 の Query 側。ADR-0031）。
 *
 * 書き込みポート [com.example.api.domain.studbook.model.breeding.BreedingResultRepository] とは別物の plain
 * interface。jMolecules `@Repository` は付けない（読み取りは Repository ビルディングブロックではない）。 種牡馬ごとの年次集計を返す
 * [BreedingResultSummaryQueries] とも別のポートで、こちらは年次レコード 1 件の
 * 詳細ビューを引く。実装（infrastructure）は集約・`BreedingResultRow` を経由せず `studbook.breeding_result` を直接読む。
 */
interface BreedingResultQueries {
    /**
     * 指定の世界の中から ID で単一繁殖成績の詳細ビューを引く。その世界に無ければ null （単純 lookup は Result を強制しない。error-handling.md）。
     */
    fun findById(worldId: WorldId, id: BreedingResultId): BreedingResultDetailView?
}
