package com.example.api.application.studbook.breeding

import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseId

/**
 * 繁殖成績の年次集計の読み取りポート（軽量 CQRS / L2 の Query 側。ADR-0031）。
 *
 * 書き込みポート [com.example.api.domain.studbook.model.breeding.BreedingResultRepository] とは別物の plain
 * interface。jMolecules `@Repository` は付けない（読み取りは Repository ビルディングブロックではない）。
 * 実装（infrastructure）は集約・`BreedingResultRow` を経由せず `breeding_result` を直接集計する。
 */
interface BreedingResultSummaryQueries {
    /** 指定した種牡馬の全種付年の集計を、種付年昇順で返す（該当なしは空リスト）。 */
    fun findByStallion(stallionId: BloodHorseId): List<BreedingResultSummaryView>
}
