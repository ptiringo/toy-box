package com.example.api.application.horseracing.racing

import com.example.api.domain.horseracing.model.horse.bloodhorse.BloodHorseId
import com.example.api.domain.horseracing.model.horse.bloodhorse.BloodHorseRepository
import com.example.api.domain.horseracing.model.racing.RacingRegistration
import com.example.api.domain.horseracing.model.racing.RacingRegistrationNumber
import com.example.api.domain.horseracing.model.racing.RacingRegistrationRepository
import com.example.api.domain.horseracing.service.racing.RegisterAsRacehorseError
import com.example.api.domain.horseracing.service.racing.registerAsRacehorse
import com.example.api.domain.shared.Command
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.binding
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.toResultOr
import java.util.UUID
import org.springframework.stereotype.Service

/**
 * 競走馬登録ユースケースの入力コマンド。
 *
 * 登録申請書に相当する境界の生入力。VO で表す項目（登録番号）は素の文字列で受け取り、ユースケース内で `create` を
 * 通して検証する。競走馬登録の対象馬は既に血統登録済みの軽種馬IDで参照する。
 *
 * @property bloodHorseId 競走馬登録する馬の軽種馬ID
 * @property registrationNumber 交付される競走馬登録番号
 */
data class RegisterAsRacehorseCommand(val bloodHorseId: UUID, val registrationNumber: String)

/** 競走馬登録時に発生しうる業務ルール違反。 */
sealed interface RegisterAsRacehorseUseCaseError {
    /** 競走馬登録番号がブランク。 */
    data object InvalidRegistrationNumber : RegisterAsRacehorseUseCaseError

    /** 競走馬登録の対象として指定された軽種馬が存在しない。 */
    data class HorseNotFound(val bloodHorseId: UUID) : RegisterAsRacehorseUseCaseError

    /**
     * ドメインサービス registerAsRacehorse の前提条件違反を application 層エラーに wrap したもの。
     *
     * 個別バリアント（馬名未登録など）は [RegisterAsRacehorseError] を参照する。
     */
    data class PreconditionViolated(val cause: RegisterAsRacehorseError) :
        RegisterAsRacehorseUseCaseError
}

/**
 * 競走馬登録ユースケース。
 *
 * 境界の生入力を VO に変換し（不正なら検証エラー）、対象馬を [BloodHorseRepository] で引き当て、ドメイン サービス registerAsRacehorse
 * で前提条件（馬名登録済み）を検証してから、成立した [RacingRegistration] を 永続化する。Controller 層は本クラスのみに依存する。
 *
 * @return 登録された [RacingRegistration]、または業務ルール違反を表す [RegisterAsRacehorseUseCaseError]
 */
@Service
class RegisterAsRacehorseUseCase(
    private val bloodHorseRepository: BloodHorseRepository,
    private val racingRegistrationRepository: RacingRegistrationRepository,
) {
    operator fun invoke(
        command: Command<RegisterAsRacehorseCommand>
    ): Result<RacingRegistration, RegisterAsRacehorseUseCaseError> = binding {
        val input = command.payload

        val registrationNumber =
            RacingRegistrationNumber.create(input.registrationNumber)
                .mapError { RegisterAsRacehorseUseCaseError.InvalidRegistrationNumber }
                .bind()

        val horse =
            bloodHorseRepository
                .findById(BloodHorseId(input.bloodHorseId))
                .toResultOr { RegisterAsRacehorseUseCaseError.HorseNotFound(input.bloodHorseId) }
                .bind()

        val racingRegistration =
            registerAsRacehorse(horse, registrationNumber)
                .mapError { RegisterAsRacehorseUseCaseError.PreconditionViolated(it) }
                .bind()

        racingRegistrationRepository.save(racingRegistration)
    }
}
