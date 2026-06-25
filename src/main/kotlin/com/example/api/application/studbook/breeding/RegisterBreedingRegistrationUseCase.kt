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
 * 繁殖登録は血統登録済みの個体を繁殖の用に供するための追加登録で、対象は登録済み軽種馬の ID で参照する。 繁殖登録番号は VO で表すため素の文字列で受け取り、ユースケース内で
 * [BreedingRegistrationNumber] の `create` を 通して検証する。付与されるロール（種牡馬／繁殖牝馬）は対象個体の性から定まるため、入力では受け取らない。
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
    /** 繁殖登録番号がブランク。 */
    data object InvalidRegistrationNumber : RegisterBreedingRegistrationUseCaseError

    /** 繁殖登録の対象として指定された軽種馬が存在しない。 */
    data class HorseNotFound(val bloodHorseId: UUID) : RegisterBreedingRegistrationUseCaseError
}

/**
 * 繁殖登録ユースケース。
 *
 * 境界の生入力を VO に変換し（不正なら検証エラー）、繁殖登録する個体を [BloodHorseRepository] で引き当て、 集約の自己検証ファクトリ
 * [BreedingRegistration.create] で繁殖登録を成立させてから永続化する。血統登録 → 繁殖登録という 順序関係は、対象が既に永続化済みの軽種馬であることを引当が
 * 要求することで自然に満たされる。
 *
 * 繁殖登録は種付記録・種付せず・分娩報告・供用停止といった繁殖の書き込み経路の起点であり、本ユースケースが その起点（`save` 経路）を開通させる。付与されるロールは対象個体の性から
 * [BreedingRegistration.create] が定める。 制度上の前提条件（馬名登録済み・競走馬登録抹消済み 等）は対応する集約が未モデル化のため現時点では検証しない。
 *
 * @return 成立した [BreedingRegistration]、または業務ルール違反を表す [RegisterBreedingRegistrationUseCaseError]
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
                .mapError { RegisterBreedingRegistrationUseCaseError.InvalidRegistrationNumber }
                .bind()

        val horse =
            bloodHorseRepository
                .findById(BloodHorseId(input.bloodHorseId))
                .toResultOr {
                    RegisterBreedingRegistrationUseCaseError.HorseNotFound(input.bloodHorseId)
                }
                .bind()

        val registration = BreedingRegistration.create(registrationNumber, horse)
        breedingRegistrationRepository.save(registration)
    }
}
