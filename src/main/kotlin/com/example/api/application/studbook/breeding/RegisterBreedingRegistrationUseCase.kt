package com.example.api.application.studbook.breeding

import com.example.api.domain.shared.Command
import com.example.api.domain.studbook.model.breeding.BreedingRegistration
import com.example.api.domain.studbook.model.breeding.BreedingRegistrationNumber
import com.example.api.domain.studbook.model.breeding.BreedingRegistrationRepository
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseId
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseRepository
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.binding
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.toResultOr
import java.util.UUID
import org.springframework.stereotype.Service

/**
 * 繁殖登録ユースケースの入力コマンド。
 *
 * 繁殖登録（JAIRS）は血統登録済みの個体を繁殖の用に供するための追加登録で、雄雌共通の単一の登録。担うロール （雄=種牡馬／雌=繁殖牝馬）は対象個体の性から定まるため、境界の生入力としては
 * 対象個体の軽種馬IDと交付される 繁殖登録番号だけを受け取る。
 *
 * @property bloodHorseId 繁殖登録する個体（血統登録済み）の軽種馬ID
 * @property registrationNumber 交付される繁殖登録番号
 */
data class RegisterBreedingRegistrationCommand(
    val bloodHorseId: UUID,
    val registrationNumber: String,
)

/** 繁殖登録時に発生しうる業務ルール違反。 */
sealed interface RegisterBreedingRegistrationUseCaseError {
    /** 繁殖登録番号がブランク（VO 検証違反）。 */
    data object BlankRegistrationNumber : RegisterBreedingRegistrationUseCaseError

    /** 繁殖登録の対象として指定された軽種馬（血統登録済みの個体）が存在しない。 */
    data class BloodHorseNotFound(val bloodHorseId: UUID) : RegisterBreedingRegistrationUseCaseError
}

/**
 * 繁殖登録ユースケース。
 *
 * 繁殖登録番号を VO 検証し、対象の軽種馬（血統登録済みの個体）を Repository で引き当てたうえで、繁殖登録集約
 * （[BreedingRegistration]）を生成して永続化する。付与されるロールはその個体の性から [BreedingRegistration.create]
 * が定めるため、本ユースケースは前提となる参照の解決と永続化のみを担う。種付記録・種付せず・分娩報告・供用停止といった
 * 繁殖の書き込み経路は、いずれもここで起こした繁殖登録を起点にする。Controller 層は本クラスのみに依存し、ドメインの 生成経路の詳細は知らない。
 *
 * @return 生成された [BreedingRegistration]、または業務ルール違反を表す [RegisterBreedingRegistrationUseCaseError]
 */
@Service
class RegisterBreedingRegistrationUseCase(
    private val bloodHorseRepository: BloodHorseRepository,
    private val breedingRegistrationRepository: BreedingRegistrationRepository,
) {
    operator fun invoke(
        command: Command<RegisterBreedingRegistrationCommand>
    ): Result<BreedingRegistration, RegisterBreedingRegistrationUseCaseError> = binding {
        val input = command.payload

        val registrationNumber =
            BreedingRegistrationNumber.create(input.registrationNumber)
                .mapError { RegisterBreedingRegistrationUseCaseError.BlankRegistrationNumber }
                .bind()

        val horse =
            bloodHorseRepository
                .findById(BloodHorseId(input.bloodHorseId))
                .toResultOr {
                    RegisterBreedingRegistrationUseCaseError.BloodHorseNotFound(input.bloodHorseId)
                }
                .bind()

        val registration = BreedingRegistration.create(registrationNumber, horse)

        breedingRegistrationRepository.save(registration)
    }
}
