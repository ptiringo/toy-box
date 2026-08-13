package com.example.api.application.studbook.breeding

import com.example.api.domain.shared.Actor
import com.example.api.domain.studbook.model.breeding.CoveringReportId
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.toResultOr
import java.util.UUID
import org.springframework.stereotype.Service

/**
 * 種付成績報告照会クエリの入力。
 *
 * 読み取り系の入力は素の DTO とし、書き込み系の [com.example.api.domain.shared.Command] 封筒（発生時刻メタデータ）は
 * 使わない。発生時刻は書き込みイベントの概念であり、読み取りには不要（ADR-0031）。
 *
 * @property id 照会対象種付成績報告の生 UUID
 */
data class GetCoveringReportQuery(val id: UUID)

/** 照会対象の種付成績報告が存在しない。URL パス上の操作対象の不在として Controller 境界で 404 に写す（api-design.md）。 */
data class CoveringReportNotFound(val id: UUID)

/**
 * 種付成績報告照会ユースケース（軽量 CQRS（L2）の読み取り側。ADR-0031）。
 *
 * 読み取りユースケースの名前は AIP の標準メソッド名（Get / List）に寄せる（`.claude/rules/architecture.md` 「読み取り経路（軽量 CQRS /
 * L2）」）。書き込みユースケース（[SubmitCoveringReportUseCase]）と同列に `@Service` で公開するが、依存するのは書き込みポートではなく読み取りポート
 * [CoveringReportQueries]。
 *
 * @return 照会できた [CoveringReportDetailView]、または対象不在を表す [CoveringReportNotFound]
 */
@Service
class GetCoveringReportUseCase(private val coveringReportQueries: CoveringReportQueries) {
    operator fun invoke(
        actor: Actor,
        query: GetCoveringReportQuery,
    ): Result<CoveringReportDetailView, CoveringReportNotFound> =
        coveringReportQueries.findById(actor.worldId, CoveringReportId(query.id)).toResultOr {
            CoveringReportNotFound(query.id)
        }
}
