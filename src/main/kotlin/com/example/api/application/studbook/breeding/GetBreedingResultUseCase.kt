package com.example.api.application.studbook.breeding

import com.example.api.domain.shared.Actor
import com.example.api.domain.studbook.model.breeding.BreedingResultId
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.toResultOr
import java.util.UUID
import org.springframework.stereotype.Service

/**
 * 繁殖成績照会クエリの入力。
 *
 * 読み取り系の入力は素の DTO とし、書き込み系の [com.example.api.domain.shared.Command] 封筒（発生時刻メタデータ）は
 * 使わない。発生時刻は書き込みイベントの概念であり、読み取りには不要（ADR-0031）。
 *
 * @property id 照会対象繁殖成績の生 UUID
 */
data class GetBreedingResultQuery(val id: UUID)

/** 照会対象の繁殖成績が存在しない。URL パス上の操作対象の不在として Controller 境界で 404 に写す（api-design.md）。 */
data class BreedingResultNotFound(val id: UUID)

/**
 * 繁殖成績照会ユースケース（軽量 CQRS（L2）の読み取り側。ADR-0031）。
 *
 * 読み取りユースケースの名前は AIP の標準メソッド名（Get / List）に寄せる（`.claude/rules/architecture.md` 「読み取り経路（軽量 CQRS /
 * L2）」）。書き込みユースケース（[RecordCoveringUseCase] 等）と同列に `@Service` で公開するが、依存するのは書き込みポートではなく読み取りポート
 * [BreedingResultQueries]。
 *
 * @return 照会できた [BreedingResultDetailView]、または対象不在を表す [BreedingResultNotFound]
 */
@Service
class GetBreedingResultUseCase(private val breedingResultQueries: BreedingResultQueries) {
    operator fun invoke(
        actor: Actor,
        query: GetBreedingResultQuery,
    ): Result<BreedingResultDetailView, BreedingResultNotFound> =
        breedingResultQueries.findById(actor.worldId, BreedingResultId(query.id)).toResultOr {
            BreedingResultNotFound(query.id)
        }
}
