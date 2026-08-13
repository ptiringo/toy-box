package com.example.api.application.studbook.breeding

import com.example.api.domain.shared.Actor
import com.example.api.domain.studbook.model.breeding.BreedingRegistrationId
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.toResultOr
import java.util.UUID
import org.springframework.stereotype.Service

/**
 * 繁殖登録照会クエリの入力。
 *
 * 読み取り系の入力は素の DTO とし、書き込み系の [com.example.api.domain.shared.Command] 封筒（発生時刻メタデータ）は
 * 使わない。発生時刻は書き込みイベントの概念であり、読み取りには不要（ADR-0031）。
 *
 * @property id 照会対象繁殖登録の生 UUID
 */
data class GetBreedingRegistrationQuery(val id: UUID)

/** 照会対象の繁殖登録が存在しない。URL パス上の操作対象の不在として Controller 境界で 404 に写す（api-design.md）。 */
data class BreedingRegistrationNotFound(val id: UUID)

/**
 * 繁殖登録照会ユースケース（軽量 CQRS（L2）の読み取り側。ADR-0031）。
 *
 * 読み取りユースケースの名前は AIP の標準メソッド名（Get / List）に寄せる（`.claude/rules/architecture.md` 「読み取り経路（軽量 CQRS /
 * L2）」）。書き込みユースケース（[RegisterBreedingRegistrationUseCase]）と同列に `@Service`
 * で公開するが、依存するのは書き込みポートではなく読み取りポート [BreedingRegistrationQueries]。
 *
 * @return 照会できた [BreedingRegistrationDetailView]、または対象不在を表す [BreedingRegistrationNotFound]
 */
@Service
class GetBreedingRegistrationUseCase(
    private val breedingRegistrationQueries: BreedingRegistrationQueries
) {
    operator fun invoke(
        actor: Actor,
        query: GetBreedingRegistrationQuery,
    ): Result<BreedingRegistrationDetailView, BreedingRegistrationNotFound> =
        breedingRegistrationQueries
            .findById(actor.worldId, BreedingRegistrationId(query.id))
            .toResultOr { BreedingRegistrationNotFound(query.id) }
}
