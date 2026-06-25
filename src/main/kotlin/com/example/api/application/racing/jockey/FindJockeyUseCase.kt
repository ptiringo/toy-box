package com.example.api.application.racing.jockey

import com.example.api.domain.racing.model.jockey.JockeyId
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.toResultOr
import java.util.UUID
import org.springframework.stereotype.Service

/**
 * ジョッキー照会クエリの入力。
 *
 * 読み取り系の入力は素の DTO とし、書き込み系の [com.example.api.domain.shared.Command] 封筒
 * （発生時刻メタデータ）は使わない。発生時刻は書き込みイベントの概念であり、読み取りには不要（ADR-0031）。
 *
 * @property id 照会対象ジョッキーの生 UUID
 */
data class FindJockeyQuery(val id: UUID)

/** 照会対象のジョッキーが存在しない。URL パス上の操作対象の不在として Controller 境界で 404 に写す（api-design.md）。 */
data class JockeyNotFound(val id: UUID)

/**
 * ジョッキー照会ユースケース（軽量 CQRS（L2）の読み取り側。ADR-0031）。
 *
 * 書き込みユースケース（[JockeyRegistrationUseCase]）と同列に `@Service` で公開するが、依存するのは 書き込みポートではなく読み取りポート
 * [JockeyQueries]。集約を組まずフラットな [JockeyView] を返す。
 *
 * @return 照会できた [JockeyView]、または対象不在を表す [JockeyNotFound]
 */
@Service
class FindJockeyUseCase(private val jockeyQueries: JockeyQueries) {
    operator fun invoke(query: FindJockeyQuery): Result<JockeyView, JockeyNotFound> =
        jockeyQueries.findById(JockeyId(query.id)).toResultOr { JockeyNotFound(query.id) }
}
