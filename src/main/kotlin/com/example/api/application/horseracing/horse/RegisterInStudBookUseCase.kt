package com.example.api.application.horseracing.horse

import com.example.api.domain.horseracing.model.horse.bloodhorse.BloodHorse
import com.example.api.domain.horseracing.model.horse.bloodhorse.BloodHorseId
import com.example.api.domain.horseracing.model.horse.bloodhorse.BloodHorseRepository
import com.example.api.domain.horseracing.model.horse.bloodhorse.BreedType
import com.example.api.domain.horseracing.model.horse.bloodhorse.Breeder
import com.example.api.domain.horseracing.model.horse.bloodhorse.CoatColor
import com.example.api.domain.horseracing.model.horse.bloodhorse.DateOfBirth
import com.example.api.domain.horseracing.model.horse.bloodhorse.DnaParentageResult
import com.example.api.domain.horseracing.model.horse.bloodhorse.MicrochipNumber
import com.example.api.domain.horseracing.model.horse.bloodhorse.PedigreeRegistrationNumber
import com.example.api.domain.horseracing.model.horse.bloodhorse.Sex
import com.example.api.domain.horseracing.model.horse.bloodhorse.StudBookEntry
import com.example.api.domain.horseracing.service.horse.RegisterInStudBookError
import com.example.api.domain.horseracing.service.horse.registerInStudBook
import com.example.api.domain.shared.Command
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.binding
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.toResultOr
import java.time.LocalDate
import java.util.UUID
import org.springframework.stereotype.Service

/**
 * 血統登録ユースケースの入力コマンド。
 *
 * 登録申請書に相当する境界の生入力。VO で表す項目（番号・マイクロチップ・生産者）は素の文字列で受け取り、ユースケース内で各 VO の `create` を通して検証する。
 * 父・母は既に血統登録済みの軽種馬IDで参照する。
 *
 * @property sireId 父（雄）の軽種馬ID
 * @property damId 母（雌）の軽種馬ID
 * @property sex 性
 * @property coatColor 毛色
 * @property breedType 品種
 * @property dateOfBirth 生年月日
 * @property breeder 生産者名
 * @property microchipNumber マイクロチップ番号
 * @property dnaParentage 申告された父母との DNA 型による親子判定結果
 * @property registrationNumber 交付される血統登録番号
 */
@Suppress("LongParameterList") // 登録申請フォーム相当の境界入力であり、項目の分割はかえって意味を損なう
data class RegisterInStudBookCommand(
    val sireId: UUID,
    val damId: UUID,
    val sex: Sex,
    val coatColor: CoatColor,
    val breedType: BreedType,
    val dateOfBirth: LocalDate,
    val breeder: String,
    val microchipNumber: String,
    val dnaParentage: DnaParentageResult,
    val registrationNumber: String,
)

/** 血統登録時に発生しうる業務ルール違反。 */
sealed interface RegisterInStudBookUseCaseError {
    /** 血統登録番号がブランク。 */
    data object InvalidRegistrationNumber : RegisterInStudBookUseCaseError

    /** マイクロチップ番号が 15 桁の数字でない。 */
    data object InvalidMicrochipNumber : RegisterInStudBookUseCaseError

    /** 生産者名がブランク。 */
    data object BlankBreeder : RegisterInStudBookUseCaseError

    /** 父として指定された軽種馬が存在しない。 */
    data class SireNotFound(val sireId: UUID) : RegisterInStudBookUseCaseError

    /** 母として指定された軽種馬が存在しない。 */
    data class DamNotFound(val damId: UUID) : RegisterInStudBookUseCaseError

    /**
     * ドメインサービス registerInStudBook の前提条件違反を application 層エラーに wrap したもの。
     *
     * 個別バリアント（父が雄でない・品種不整合など）は [RegisterInStudBookError] を参照する。
     */
    data class PreconditionViolated(val cause: RegisterInStudBookError) :
        RegisterInStudBookUseCaseError
}

/**
 * 血統登録ユースケース。
 *
 * 境界の生入力を VO に変換し（不正なら検証エラー）、父・母を [BloodHorseRepository] で引き当て、ドメインサービス registerInStudBook
 * で前提条件（父=雄・母=雌・DNA 親子整合・品種整合）を検証してから、誕生した [BloodHorse] を 永続化する。Controller
 * 層は本クラスのみに依存し、ポートやドメインサービスは知らない。
 *
 * @return 登録された [BloodHorse]、または業務ルール違反を表す [RegisterInStudBookUseCaseError]
 */
@Service
class RegisterInStudBookUseCase(private val bloodHorseRepository: BloodHorseRepository) {
    operator fun invoke(
        command: Command<RegisterInStudBookCommand>
    ): Result<BloodHorse, RegisterInStudBookUseCaseError> = binding {
        val input = command.payload

        val registrationNumber =
            PedigreeRegistrationNumber.create(input.registrationNumber)
                .mapError { RegisterInStudBookUseCaseError.InvalidRegistrationNumber }
                .bind()
        val microchipNumber =
            MicrochipNumber.create(input.microchipNumber)
                .mapError { RegisterInStudBookUseCaseError.InvalidMicrochipNumber }
                .bind()
        val breeder =
            Breeder.create(input.breeder)
                .mapError { RegisterInStudBookUseCaseError.BlankBreeder }
                .bind()

        val sire =
            bloodHorseRepository
                .findById(BloodHorseId(input.sireId))
                .toResultOr { RegisterInStudBookUseCaseError.SireNotFound(input.sireId) }
                .bind()
        val dam =
            bloodHorseRepository
                .findById(BloodHorseId(input.damId))
                .toResultOr { RegisterInStudBookUseCaseError.DamNotFound(input.damId) }
                .bind()

        val entry =
            StudBookEntry(
                sex = input.sex,
                coatColor = input.coatColor,
                breedType = input.breedType,
                dateOfBirth = DateOfBirth(input.dateOfBirth),
                breeder = breeder,
                microchipNumber = microchipNumber,
                dnaParentage = input.dnaParentage,
            )

        val bloodHorse =
            registerInStudBook(sire, dam, entry, registrationNumber)
                .mapError { RegisterInStudBookUseCaseError.PreconditionViolated(it) }
                .bind()

        bloodHorseRepository.save(bloodHorse)
    }
}
