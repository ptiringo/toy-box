package com.example.api.application.studbook.breeding

import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseId
import org.springframework.stereotype.Service

/**
 * 繁殖成績年次集計の照会クエリの入力。
 *
 * 読み取り系の入力は素の DTO とし、書き込み系の [com.example.api.domain.shared.Command] 封筒 （発生時刻メタデータ）は使わない（ADR-0031）。
 *
 * @property stallionId 照会対象の種牡馬の `BloodHorseId`
 */
data class FindBreedingResultSummaryQuery(val stallionId: BloodHorseId)

/**
 * 繁殖成績の年次集計を照会するユースケース（軽量 CQRS / L2 の読み取り側。ADR-0031）。
 *
 * 読み取りポート [BreedingResultSummaryQueries] に委譲し、(種牡馬, 種付年) 単位の集計一覧を返す。
 * コレクション照会のため失敗バリアントは設けない（該当なし＝空リスト）。
 */
@Service
class FindBreedingResultSummaryUseCase(
    private val breedingResultSummaryQueries: BreedingResultSummaryQueries
) {
    operator fun invoke(query: FindBreedingResultSummaryQuery): List<BreedingResultSummaryView> =
        breedingResultSummaryQueries.findByStallion(query.stallionId)
}
