package com.example.api.application.studbook.horse

import com.example.api.domain.shared.Actor
import com.example.api.domain.shared.Command
import com.example.api.domain.studbook.model.breeding.BreedingRegistrationRepository
import com.example.api.domain.studbook.model.breeding.BreedingResultId
import com.example.api.domain.studbook.model.breeding.BreedingResultRepository
import com.example.api.domain.studbook.model.horse.bloodhorse.BlankBreeder
import com.example.api.domain.studbook.model.horse.bloodhorse.BlankPedigreeRegistrationNumber
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseRepository
import com.example.api.domain.studbook.model.horse.bloodhorse.BreedType
import com.example.api.domain.studbook.model.horse.bloodhorse.Breeder
import com.example.api.domain.studbook.model.horse.bloodhorse.CoatColor
import com.example.api.domain.studbook.model.horse.bloodhorse.FoalIdentity
import com.example.api.domain.studbook.model.horse.bloodhorse.PedigreeRegistrationNumber
import com.example.api.domain.studbook.model.horse.bloodhorse.Sex
import com.example.api.domain.studbook.model.inspection.DnaParentageResult
import com.example.api.domain.studbook.model.inspection.HorseInspection
import com.example.api.domain.studbook.model.inspection.HorseInspectionRepository
import com.example.api.domain.studbook.model.inspection.InvalidMicrochipNumber
import com.example.api.domain.studbook.model.inspection.MicrochipNumber
import com.example.api.domain.studbook.model.inspection.ParentageDetermination
import com.example.api.domain.studbook.service.horse.RegisterBroughtInFoalError
import com.example.api.domain.studbook.service.horse.ensurePedigreeRegistrationNumberAvailable
import com.example.api.domain.studbook.service.horse.registerBroughtInFoal
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.binding
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.toResultOr
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 持込馬血統登録ユースケースの入力コマンド。
 *
 * 海外種付の産駒（持込馬）の血統登録申請に相当する境界の生入力（#624 / ADR-0087）。国内種付の産駒の [RegisterFoalCommand]
 * と同じく、父・母・出生日は申請者が持ち込まず、報告済みの繁殖成績（[breedingResultId]）から定まる:
 * - 父 = 海外種付（`Covering.Overseas.sire`）の外国種牡馬
 * - 母 = 繁殖登録（`breedingRegistration.registeredHorseId`、ロールは繁殖牝馬）の繁殖牝馬
 * - 出生日 = 分娩結果（`FoalingOutcome.LiveFoal.foalingDate`）
 *
 * よって本コマンドは繁殖成績IDと、仔馬自身の個体識別（生年月日・父母を除く）のみを受け取る。VO で表す項目（番号・ マイクロチップ・生産者）は素の文字列で受け取り、ユースケース内で各 VO の
 * `create` を通して検証する。
 *
 * @property breedingResultId 産駒が生じた繁殖成績ID（種付が海外種付で、分娩結果が報告済みであること）
 * @property sex 性
 * @property coatColor 毛色
 * @property breedType 品種
 * @property breeder 生産者名
 * @property microchipNumber マイクロチップ番号
 * @property dnaParentage 申告された父母との DNA 型による親子判定結果
 * @property registrationNumber 交付される血統登録番号
 */
data class RegisterBroughtInFoalCommand(
    val breedingResultId: UUID,
    val sex: Sex,
    val coatColor: CoatColor,
    val breedType: BreedType,
    val breeder: String,
    val microchipNumber: String,
    val dnaParentage: DnaParentageResult,
    val registrationNumber: String,
)

/** 持込馬血統登録時に発生しうる業務ルール違反。 */
sealed interface RegisterBroughtInFoalUseCaseError {
    /** 血統登録番号がブランク。 */
    data object InvalidRegistrationNumber : RegisterBroughtInFoalUseCaseError

    /** 血統登録番号が既にこの世界の他の軽種馬に採番済み。 */
    data class RegistrationNumberAlreadyTaken(val registrationNumber: String) :
        RegisterBroughtInFoalUseCaseError

    /** マイクロチップ番号が 15 桁の数字でない。 */
    data object InvalidMicrochipNumber : RegisterBroughtInFoalUseCaseError

    /** 生産者名がブランク。 */
    data object BlankBreeder : RegisterBroughtInFoalUseCaseError

    /** 登録対象として指定された繁殖成績が存在しない。 */
    data class BreedingResultNotFound(val breedingResultId: UUID) :
        RegisterBroughtInFoalUseCaseError

    /** 繁殖成績が紐づく繁殖登録が存在しない（母＝繁殖牝馬を解決できない）。 */
    data class BreedingRegistrationNotFound(val breedingRegistrationId: UUID) :
        RegisterBroughtInFoalUseCaseError

    /** 母（繁殖登録の繁殖牝馬）として参照される軽種馬が存在しない。 */
    data class DamNotFound(val damId: UUID) : RegisterBroughtInFoalUseCaseError

    /**
     * ドメインサービス registerBroughtInFoal の前提条件違反を application 層エラーに wrap したもの。
     *
     * 個別バリアント（種付が海外種付でない・分娩結果が生産でない・母が雌でない・品種不整合など）は [RegisterBroughtInFoalError] を参照する。
     */
    data class PreconditionViolated(val cause: RegisterBroughtInFoalError) :
        RegisterBroughtInFoalUseCaseError
}

/**
 * 持込馬血統登録ユースケース。
 *
 * 境界の生入力を VO に変換し（不正なら検証エラー）、繁殖成績・繁殖登録・母（繁殖牝馬）を各ポートで引き当て、ドメインサービス registerBroughtInFoal
 * で前提条件（種付が海外種付であること・分娩結果が生産であること、および委譲先 `BloodHorse.createBroughtIn` の母=雌・DNA
 * 親子整合・品種整合・毛色整合）を検証してから、誕生した軽種馬を永続化する。父は外国種牡馬で 日本の軽種馬として存在しないため、国内種付の [RegisterFoalUseCase]
 * と異なり父は引き当てない。
 *
 * 血統登録番号の一意性は、原簿を共有する以上どの登録経路でも等しく成り立つ必要があるため、他の登録経路と同じ ドメインサービス
 * [ensurePedigreeRegistrationNumberAvailable] で引き当てる（ADR-0022）。
 *
 * @return 登録された [RegisteredBloodHorse]、または業務ルール違反を表す [RegisterBroughtInFoalUseCaseError]
 */
@Service
class RegisterBroughtInFoalUseCase(
    private val breedingResultRepository: BreedingResultRepository,
    private val breedingRegistrationRepository: BreedingRegistrationRepository,
    private val bloodHorseRepository: BloodHorseRepository,
    private val horseInspectionRepository: HorseInspectionRepository,
) {
    @Transactional
    operator fun invoke(
        actor: Actor,
        command: Command<RegisterBroughtInFoalCommand>,
    ): Result<RegisteredBloodHorse, RegisterBroughtInFoalUseCaseError> = binding {
        val input = command.payload

        val registrationNumber =
            PedigreeRegistrationNumber.create(input.registrationNumber)
                .mapError { _: BlankPedigreeRegistrationNumber ->
                    RegisterBroughtInFoalUseCaseError.InvalidRegistrationNumber
                }
                .bind()
        ensurePedigreeRegistrationNumberAvailable(
                actor.worldId,
                registrationNumber,
                bloodHorseRepository,
            )
            .mapError {
                RegisterBroughtInFoalUseCaseError.RegistrationNumberAlreadyTaken(it.number.value)
            }
            .bind()
        // 審査をメモリ内で組み立てる。前提条件検証（registerBroughtInFoal）を通った後にのみ永続化し、
        // 業務ルール違反での却下時に孤児レコードが残るのを防ぐ。
        val inspection = buildInspection(input).bind()
        val foalIdentity = buildFoalIdentity(input).bind()

        val breedingResult =
            breedingResultRepository
                .findById(actor.worldId, BreedingResultId(input.breedingResultId))
                .toResultOr {
                    RegisterBroughtInFoalUseCaseError.BreedingResultNotFound(input.breedingResultId)
                }
                .bind()

        val breedingRegistration =
            breedingRegistrationRepository
                .findById(actor.worldId, breedingResult.breedingRegistrationId)
                .toResultOr {
                    RegisterBroughtInFoalUseCaseError.BreedingRegistrationNotFound(
                        breedingResult.breedingRegistrationId.value
                    )
                }
                .bind()

        val damId = breedingRegistration.registeredHorseId
        val dam =
            bloodHorseRepository
                .findById(actor.worldId, damId)
                .toResultOr { RegisterBroughtInFoalUseCaseError.DamNotFound(damId.value) }
                .bind()

        val bloodHorse =
            registerBroughtInFoal(breedingResult, dam, foalIdentity, inspection, registrationNumber)
                .mapError { RegisterBroughtInFoalUseCaseError.PreconditionViolated(it) }
                .bind()

        // 審査と軽種馬の 2 集約書き込みは invoke の @Transactional 境界内で原子的に行う（#483）。
        horseInspectionRepository.save(actor.worldId, inspection)
        val saved =
            bloodHorseRepository.save(actor.worldId, bloodHorse).getOrElse {
                error("新規の軽種馬の保存で楽観ロック競合はありえない: id=${bloodHorse.id.value}")
            }
        RegisteredBloodHorse(saved, inspection)
    }

    /** 仔馬の個体識別を組み立てる（生産者を VO 検証）。形式不正は 400 系の入力エラーにマップする。 */
    private fun buildFoalIdentity(
        input: RegisterBroughtInFoalCommand
    ): Result<FoalIdentity, RegisterBroughtInFoalUseCaseError> = binding {
        val breeder =
            Breeder.create(input.breeder)
                .mapError { _: BlankBreeder -> RegisterBroughtInFoalUseCaseError.BlankBreeder }
                .bind()
        FoalIdentity(
            sex = input.sex,
            coatColor = input.coatColor,
            breedType = input.breedType,
            breeder = breeder,
        )
    }

    /** 審査を組み立てる（マイクロチップ番号を VO 検証）。形式不正は 400 系の入力エラーにマップする。 */
    private fun buildInspection(
        input: RegisterBroughtInFoalCommand
    ): Result<HorseInspection, RegisterBroughtInFoalUseCaseError> = binding {
        val microchipNumber =
            MicrochipNumber.create(input.microchipNumber)
                .mapError { _: InvalidMicrochipNumber ->
                    RegisterBroughtInFoalUseCaseError.InvalidMicrochipNumber
                }
                .bind()
        HorseInspection.create(
            microchipNumber = microchipNumber,
            parentage = ParentageDetermination.ByDna(input.dnaParentage),
        )
    }
}
