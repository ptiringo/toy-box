package com.example.api.application.studbook.breeding

import com.example.api.domain.shared.Actor
import com.example.api.domain.shared.Command
import com.example.api.domain.shared.UpdateConflict
import com.example.api.domain.studbook.model.breeding.BreedingRegistration
import com.example.api.domain.studbook.model.breeding.BreedingRegistrationId
import com.example.api.domain.studbook.model.breeding.BreedingRegistrationRepository
import com.example.api.domain.studbook.model.breeding.BreedingRetirement
import com.example.api.domain.studbook.model.breeding.RetirementReason
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.binding
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.toResultOr
import java.time.LocalDate
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 繁殖供用停止ユースケースの入力コマンド。
 *
 * 血統書に記載する「供用停止の事由とその事由が発生した日」（登録規程実施基準・第15条第1項(3)）に相当する境界の入力。 供用停止する繁殖登録を ID で参照し、事由はドメインの
 * [RetirementReason] で受け取る。
 *
 * @property breedingRegistrationId 供用停止する繁殖登録ID
 * @property reason 供用停止の事由
 * @property occurredOn 事由が発生した日
 */
data class RetireBreedingRegistrationCommand(
    val breedingRegistrationId: UUID,
    val reason: RetirementReason,
    val occurredOn: LocalDate,
)

/** 繁殖供用停止時に発生しうる業務ルール違反。 */
sealed interface RetireBreedingRegistrationUseCaseError {
    /** 供用停止の対象として指定された繁殖登録が存在しない。 */
    data class BreedingRegistrationNotFound(val breedingRegistrationId: UUID) :
        RetireBreedingRegistrationUseCaseError

    /**
     * 既に供用停止済みの繁殖登録を重ねて供用停止しようとした。
     *
     * 供用停止は繁殖登録ライフサイクルの終端で一度だけ行えるため、二重供用停止は不変条件違反。
     *
     * @property current 既に記録されている供用停止
     */
    data class AlreadyRetired(val current: BreedingRetirement) :
        RetireBreedingRegistrationUseCaseError

    /**
     * 読み取りから供用停止確定までの間に、対象の繁殖登録が他の更新と競合した。
     *
     * 楽観ロック（読み取り時点の version との不一致）による検出。最新の状態を取得して再操作すれば解消しうる。
     *
     * @property breedingRegistrationId 競合した繁殖登録ID
     */
    data class ConcurrentModification(val breedingRegistrationId: UUID) :
        RetireBreedingRegistrationUseCaseError
}

/**
 * 繁殖供用停止ユースケース。
 *
 * 供用停止する繁殖登録を [BreedingRegistrationRepository] で引き当て、集約の [BreedingRegistration.retire] で
 * 供用停止を記録（二重供用停止は不変条件違反）してから、楽観ロック付きで更新する（競合は
 * [RetireBreedingRegistrationUseCaseError.ConcurrentModification]）。繁殖登録にとって本番で初めての update 経路であり、
 * insert 専用の [RegisterBreedingRegistrationUseCase] と違って競合は現実に起こりうる。
 *
 * @return 供用停止済みの [BreedingRegistration]、または業務ルール違反を表す [RetireBreedingRegistrationUseCaseError]
 */
@Service
class RetireBreedingRegistrationUseCase(
    private val breedingRegistrationRepository: BreedingRegistrationRepository
) {
    @Transactional
    operator fun invoke(
        actor: Actor,
        command: Command<RetireBreedingRegistrationCommand>,
    ): Result<BreedingRegistration, RetireBreedingRegistrationUseCaseError> = binding {
        val input = command.payload

        val registration =
            breedingRegistrationRepository
                .findById(actor.worldId, BreedingRegistrationId(input.breedingRegistrationId))
                .toResultOr {
                    RetireBreedingRegistrationUseCaseError.BreedingRegistrationNotFound(
                        input.breedingRegistrationId
                    )
                }
                .bind()

        val retired =
            registration
                .retire(input.reason, input.occurredOn)
                .mapError { RetireBreedingRegistrationUseCaseError.AlreadyRetired(it.current) }
                .bind()

        breedingRegistrationRepository
            .save(actor.worldId, retired)
            .mapError { _: UpdateConflict ->
                RetireBreedingRegistrationUseCaseError.ConcurrentModification(
                    input.breedingRegistrationId
                )
            }
            .bind()
    }
}
