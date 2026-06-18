package com.example.api.application.horseracing.horse

import com.example.api.domain.horseracing.model.horse.bloodhorse.BloodHorse
import com.example.api.domain.horseracing.model.horse.bloodhorse.BloodHorseId
import com.example.api.domain.horseracing.model.horse.bloodhorse.BloodHorseRepository
import com.example.api.domain.horseracing.model.horse.bloodhorse.HorseName
import com.example.api.domain.shared.Command
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.binding
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.toResultOr
import java.util.UUID
import org.springframework.stereotype.Service

/**
 * 馬名登録ユースケースの入力コマンド。
 *
 * 馬名登録は血統登録済みの個体を対象とするため、対象は登録済み軽種馬の ID で参照する。馬名は VO で表すため 素の文字列で受け取り、ユースケース内で [HorseName] の
 * `create` を通して検証する。
 *
 * @property bloodHorseId 命名対象の軽種馬ID
 * @property name 付与する馬名
 */
data class NameHorseCommand(val bloodHorseId: UUID, val name: String)

/** 馬名登録時に発生しうる業務ルール違反。 */
sealed interface NameHorseUseCaseError {
    /** 馬名が不変条件（カタカナ2〜9文字）を満たさない。 */
    data object InvalidName : NameHorseUseCaseError

    /** 命名対象として指定された軽種馬が存在しない。 */
    data class HorseNotFound(val bloodHorseId: UUID) : NameHorseUseCaseError

    /**
     * 対象の軽種馬が既に命名済みで、二重命名はできない。
     *
     * @property currentName 既に付与されている馬名
     */
    data class AlreadyNamed(val currentName: String) : NameHorseUseCaseError
}

/**
 * 馬名登録ユースケース。
 *
 * 境界の生入力を [HorseName] に変換し（不正なら検証エラー）、対象の軽種馬を [BloodHorseRepository] で引き当て、 集約の `assignName`
 * で命名状態を遷移させてから永続化する。血統登録 → 馬名登録という順序関係は、対象が 既に永続化済みの [BloodHorse] であることを引当が要求することで自然に満たされる。
 *
 * @return 命名された [BloodHorse]、または業務ルール違反を表す [NameHorseUseCaseError]
 */
@Service
class NameHorseUseCase(private val bloodHorseRepository: BloodHorseRepository) {
    operator fun invoke(
        command: Command<NameHorseCommand>
    ): Result<BloodHorse, NameHorseUseCaseError> = binding {
        val input = command.payload

        val horseName =
            HorseName.create(input.name).mapError { NameHorseUseCaseError.InvalidName }.bind()

        val bloodHorse =
            bloodHorseRepository
                .findById(BloodHorseId(input.bloodHorseId))
                .toResultOr { NameHorseUseCaseError.HorseNotFound(input.bloodHorseId) }
                .bind()

        val named =
            bloodHorse
                .assignName(horseName)
                .mapError { NameHorseUseCaseError.AlreadyNamed(it.currentName.value) }
                .bind()

        bloodHorseRepository.save(named)
    }
}
