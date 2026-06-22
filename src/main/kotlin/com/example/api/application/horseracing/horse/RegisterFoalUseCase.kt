package com.example.api.application.horseracing.horse

import com.example.api.domain.horseracing.model.breeding.BreedingRegistrationRepository
import com.example.api.domain.horseracing.model.breeding.BreedingResult
import com.example.api.domain.horseracing.model.breeding.BreedingResultId
import com.example.api.domain.horseracing.model.breeding.BreedingResultRepository
import com.example.api.domain.horseracing.model.horse.bloodhorse.BloodHorse
import com.example.api.domain.horseracing.model.horse.bloodhorse.BloodHorseRepository
import com.example.api.domain.horseracing.model.horse.bloodhorse.BreedType
import com.example.api.domain.horseracing.model.horse.bloodhorse.Breeder
import com.example.api.domain.horseracing.model.horse.bloodhorse.CoatColor
import com.example.api.domain.horseracing.model.horse.bloodhorse.DnaParentageResult
import com.example.api.domain.horseracing.model.horse.bloodhorse.FoalIdentity
import com.example.api.domain.horseracing.model.horse.bloodhorse.MicrochipNumber
import com.example.api.domain.horseracing.model.horse.bloodhorse.PedigreeRegistrationNumber
import com.example.api.domain.horseracing.model.horse.bloodhorse.Sex
import com.example.api.domain.horseracing.service.horse.RegisterFoalError
import com.example.api.domain.horseracing.service.horse.registerFoal
import com.example.api.domain.shared.Command
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.binding
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.toResultOr
import java.util.UUID
import org.springframework.stereotype.Service

/**
 * 生産産駒登録ユースケースの入力コマンド。
 *
 * 生産（分娩）された仔馬の血統登録申請に相当する境界の生入力。父・母・出生日は申請者が持ち込まず、報告済みの 繁殖成績（[breedingResultId]）から定まる:
 * - 父 = 種付（`covering.stallionId`）の種牡馬
 * - 母 = 繁殖登録（`breedingRegistration.registeredHorseId`、ロールは繁殖牝馬）の繁殖牝馬
 * - 出生日 = 分娩結果（`FoalingOutcome.LiveFoal.foalingDate`）
 *
 * よって本コマンドは繁殖成績IDと、仔馬自身の個体識別（生年月日・父母を除く）のみを受け取る。VO で表す項目（番号・ マイクロチップ・生産者）は素の文字列で受け取り、ユースケース内で各 VO の
 * `create` を通して検証する。
 *
 * @property breedingResultId 産駒が生じた繁殖成績ID（分娩結果が報告済みであること）
 * @property sex 性
 * @property coatColor 毛色
 * @property breedType 品種
 * @property breeder 生産者名
 * @property microchipNumber マイクロチップ番号
 * @property dnaParentage 申告された父母との DNA 型による親子判定結果
 * @property registrationNumber 交付される血統登録番号
 */
data class RegisterFoalCommand(
    val breedingResultId: UUID,
    val sex: Sex,
    val coatColor: CoatColor,
    val breedType: BreedType,
    val breeder: String,
    val microchipNumber: String,
    val dnaParentage: DnaParentageResult,
    val registrationNumber: String,
)

/** 生産産駒登録時に発生しうる業務ルール違反。 */
sealed interface RegisterFoalUseCaseError {
    /** 血統登録番号がブランク。 */
    data object InvalidRegistrationNumber : RegisterFoalUseCaseError

    /** マイクロチップ番号が 15 桁の数字でない。 */
    data object InvalidMicrochipNumber : RegisterFoalUseCaseError

    /** 生産者名がブランク。 */
    data object BlankBreeder : RegisterFoalUseCaseError

    /** 登録対象として指定された繁殖成績が存在しない。 */
    data class BreedingResultNotFound(val breedingResultId: UUID) : RegisterFoalUseCaseError

    /** 繁殖成績が紐づく繁殖登録が存在しない（母＝繁殖牝馬を解決できない）。 */
    data class BreedingRegistrationNotFound(val breedingRegistrationId: UUID) :
        RegisterFoalUseCaseError

    /** 父（種付の種牡馬）として参照される軽種馬が存在しない。 */
    data class SireNotFound(val sireId: UUID) : RegisterFoalUseCaseError

    /** 母（繁殖登録の繁殖牝馬）として参照される軽種馬が存在しない。 */
    data class DamNotFound(val damId: UUID) : RegisterFoalUseCaseError

    /**
     * ドメインサービス registerFoal の前提条件違反を application 層エラーに wrap したもの。
     *
     * 個別バリアント（分娩結果が生産でない・父が雄でない・品種不整合など）は [RegisterFoalError] を参照する。
     */
    data class PreconditionViolated(val cause: RegisterFoalError) : RegisterFoalUseCaseError
}

/**
 * 生産産駒登録ユースケース。
 *
 * 境界の生入力を VO に変換し（不正なら検証エラー）、繁殖成績・繁殖登録・父（種付の種牡馬）・母（繁殖牝馬）を 各ポートで引き当て、ドメインサービス registerFoal
 * で前提条件（分娩結果が生産であること、および委譲先 registerInStudBook の父=雄・母=雌・DNA 親子整合・品種整合）を検証してから、誕生した [BloodHorse]
 * を永続化する。 Controller 層は本クラスのみに依存し、ポートやドメインサービスは知らない。
 *
 * @return 登録された [BloodHorse]、または業務ルール違反を表す [RegisterFoalUseCaseError]
 */
@Service
class RegisterFoalUseCase(
    private val breedingResultRepository: BreedingResultRepository,
    private val breedingRegistrationRepository: BreedingRegistrationRepository,
    private val bloodHorseRepository: BloodHorseRepository,
) {
    operator fun invoke(
        command: Command<RegisterFoalCommand>
    ): Result<BloodHorse, RegisterFoalUseCaseError> = binding {
        val input = command.payload

        val registrationNumber =
            PedigreeRegistrationNumber.create(input.registrationNumber)
                .mapError { RegisterFoalUseCaseError.InvalidRegistrationNumber }
                .bind()
        val microchipNumber =
            MicrochipNumber.create(input.microchipNumber)
                .mapError { RegisterFoalUseCaseError.InvalidMicrochipNumber }
                .bind()
        val breeder =
            Breeder.create(input.breeder).mapError { RegisterFoalUseCaseError.BlankBreeder }.bind()

        val breedingResult =
            breedingResultRepository
                .findById(BreedingResultId(input.breedingResultId))
                .toResultOr {
                    RegisterFoalUseCaseError.BreedingResultNotFound(input.breedingResultId)
                }
                .bind()

        val breedingRegistration =
            breedingRegistrationRepository
                .findById(breedingResult.breedingRegistrationId)
                .toResultOr {
                    RegisterFoalUseCaseError.BreedingRegistrationNotFound(
                        breedingResult.breedingRegistrationId.value
                    )
                }
                .bind()

        val sire = resolveSire(breedingResult).bind()

        val damId = breedingRegistration.registeredHorseId
        val dam =
            bloodHorseRepository
                .findById(damId)
                .toResultOr { RegisterFoalUseCaseError.DamNotFound(damId.value) }
                .bind()

        val foalIdentity =
            FoalIdentity(
                sex = input.sex,
                coatColor = input.coatColor,
                breedType = input.breedType,
                breeder = breeder,
                microchipNumber = microchipNumber,
                dnaParentage = input.dnaParentage,
            )

        val bloodHorse =
            registerFoal(breedingResult, sire, dam, foalIdentity, registrationNumber)
                .mapError { RegisterFoalUseCaseError.PreconditionViolated(it) }
                .bind()

        bloodHorseRepository.save(bloodHorse)
    }

    /**
     * 繁殖成績から父（種付の種牡馬）を解決する。
     *
     * 種付せず（covering が無い）の年次成績からは産駒が生じず父も定まらないため、父の解決前に、ドメインサービスと
     * 同じ「生産でない」前提条件違反（[RegisterFoalError.NotLiveFoal]）へ寄せて短絡する。種付がある場合のみ種牡馬を [BloodHorseRepository]
     * で引き当てる。
     */
    private fun resolveSire(
        breedingResult: BreedingResult
    ): Result<BloodHorse, RegisterFoalUseCaseError> = binding {
        val covering =
            breedingResult.covering
                .toResultOr {
                    RegisterFoalUseCaseError.PreconditionViolated(
                        RegisterFoalError.NotLiveFoal(breedingResult.outcome)
                    )
                }
                .bind()
        val sireId = covering.stallionId
        bloodHorseRepository
            .findById(sireId)
            .toResultOr { RegisterFoalUseCaseError.SireNotFound(sireId.value) }
            .bind()
    }
}
