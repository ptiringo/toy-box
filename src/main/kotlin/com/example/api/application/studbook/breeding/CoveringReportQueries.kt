package com.example.api.application.studbook.breeding

import com.example.api.domain.shared.WorldId
import com.example.api.domain.studbook.model.breeding.CoveringReportId

/**
 * 種付成績報告の読み取りポート（軽量 CQRS / L2 の Query 側。ADR-0031）。
 *
 * 書き込みポート [com.example.api.domain.studbook.model.breeding.CoveringReportRepository] とは別物の plain
 * interface。jMolecules `@Repository` は付けない（読み取りは Repository ビルディングブロックではない）。
 * 実装（infrastructure）は集約・`CoveringReportRow` を経由せず `studbook.covering_report` を直接読む。
 */
interface CoveringReportQueries {
    /**
     * 指定の世界の中から ID で単一種付成績報告の詳細ビューを引く。その世界に無ければ null （単純 lookup は Result を強制しない。error-handling.md）。
     */
    fun findById(worldId: WorldId, id: CoveringReportId): CoveringReportDetailView?
}
