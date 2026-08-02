package com.example.api.application.studbook.horse

import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseId
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.toResultOr
import java.util.UUID
import org.springframework.stereotype.Service

/**
 * 軽種馬照会クエリの入力。
 *
 * 読み取り系の入力は素の DTO とし、書き込み系の [com.example.api.domain.shared.Command] 封筒
 * （発生時刻メタデータ）は使わない。発生時刻は書き込みイベントの概念であり、読み取りには不要（ADR-0031）。
 *
 * @property id 照会対象軽種馬の生 UUID
 */
data class GetBloodHorseQuery(val id: UUID)

/** 照会対象の軽種馬が存在しない。URL パス上の操作対象の不在として Controller 境界で 404 に写す（api-design.md）。 */
data class BloodHorseNotFound(val id: UUID)

/**
 * 軽種馬照会ユースケース（軽量 CQRS（L2）の読み取り側。ADR-0031）。
 *
 * 一覧の [FindBloodHorsesUseCase] と対になる by-id 照会。名前は AIP の標準メソッド名（List / Get）に寄せ、
 * 一覧（`FindBloodHorses〜`）との差が末尾の `s` 一文字だけになるのを避ける。
 *
 * 書き込みユースケース（[RegisterInStudBookUseCase] 等）と同列に `@Service` で公開するが、依存するのは 書き込みポートではなく読み取りポート
 * [BloodHorseQueries]。集約を組まず [BloodHorseDetailView] を返す。
 *
 * @return 照会できた [BloodHorseDetailView]、または対象不在を表す [BloodHorseNotFound]
 */
@Service
class GetBloodHorseUseCase(private val bloodHorseQueries: BloodHorseQueries) {
    operator fun invoke(
        query: GetBloodHorseQuery
    ): Result<BloodHorseDetailView, BloodHorseNotFound> =
        bloodHorseQueries.findById(BloodHorseId(query.id)).toResultOr {
            BloodHorseNotFound(query.id)
        }
}
