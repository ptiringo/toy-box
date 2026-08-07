package com.example.api.application.studbook.inspection

import com.example.api.domain.shared.Actor
import com.example.api.domain.studbook.model.inspection.HorseInspectionId
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.toResultOr
import java.util.UUID
import org.springframework.stereotype.Service

/**
 * 審査照会クエリの入力。
 *
 * 読み取り系の入力は素の DTO とし、書き込み系の [com.example.api.domain.shared.Command] 封筒
 * （発生時刻メタデータ）は使わない。発生時刻は書き込みイベントの概念であり、読み取りには不要（ADR-0031）。
 *
 * @property id 照会対象審査の生 UUID
 */
data class GetHorseInspectionQuery(val id: UUID)

/** 照会対象の審査が存在しない。URL パス上の操作対象の不在として Controller 境界で 404 に写す（api-design.md）。 */
data class HorseInspectionNotFound(val id: UUID)

/**
 * 審査照会ユースケース（軽量 CQRS（L2）の読み取り側。ADR-0031）。
 *
 * 書き込みユースケース（[RecordHorseInspectionUseCase]）と同列に `@Service` で公開するが、依存するのは 書き込みポートではなく読み取りポート
 * [HorseInspectionQueries]。集約を組まず [HorseInspectionView] を返す。
 *
 * @return 照会できた [HorseInspectionView]、または対象不在を表す [HorseInspectionNotFound]
 */
@Service
class GetHorseInspectionUseCase(private val horseInspectionQueries: HorseInspectionQueries) {
    operator fun invoke(
        actor: Actor,
        query: GetHorseInspectionQuery,
    ): Result<HorseInspectionView, HorseInspectionNotFound> =
        horseInspectionQueries.findById(actor.worldId, HorseInspectionId(query.id)).toResultOr {
            HorseInspectionNotFound(query.id)
        }
}
