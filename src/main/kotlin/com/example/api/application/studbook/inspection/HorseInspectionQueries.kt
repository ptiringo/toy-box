package com.example.api.application.studbook.inspection

import com.example.api.domain.studbook.model.inspection.HorseInspectionId

/**
 * 審査の読み取りポート（軽量 CQRS（L2）の Query 側。ADR-0031）。
 *
 * 書き込みポート [com.example.api.domain.studbook.model.inspection.HorseInspectionRepository]
 * とは**別物として割る**。同じストアを読んでも、経路（write=集約復元 / read=View 直組み）と モデルを分離するのが L2 の価値であり、「同じテーブルなら write
 * ポートに finder を生やせばよい」 という誘惑には乗らない。
 *
 * 読み取りポートは集約のライフサイクルを持たないため、書き込みポートが付ける jMolecules `@Repository` は**付けない**。
 */
interface HorseInspectionQueries {
    /** ID で審査ビューを引く。存在しなければ null（単純 lookup は Result を強制しない。error-handling.md）。 */
    fun findById(id: HorseInspectionId): HorseInspectionView?
}
