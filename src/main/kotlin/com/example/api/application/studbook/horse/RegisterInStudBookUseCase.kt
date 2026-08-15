package com.example.api.application.studbook.horse

import com.example.api.domain.shared.Actor
import com.example.api.domain.shared.Command
import com.example.api.domain.studbook.model.horse.bloodhorse.BlankBreeder
import com.example.api.domain.studbook.model.horse.bloodhorse.BlankPedigreeRegistrationNumber
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorse
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseId
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseRepository
import com.example.api.domain.studbook.model.horse.bloodhorse.BreedType
import com.example.api.domain.studbook.model.horse.bloodhorse.Breeder
import com.example.api.domain.studbook.model.horse.bloodhorse.CoatColor
import com.example.api.domain.studbook.model.horse.bloodhorse.DateOfBirth
import com.example.api.domain.studbook.model.horse.bloodhorse.PedigreeRegistrationNumber
import com.example.api.domain.studbook.model.horse.bloodhorse.RegisterInStudBookError
import com.example.api.domain.studbook.model.horse.bloodhorse.Sex
import com.example.api.domain.studbook.model.horse.bloodhorse.StudBookEntry
import com.example.api.domain.studbook.model.inspection.DnaParentageResult
import com.example.api.domain.studbook.model.inspection.HorseInspection
import com.example.api.domain.studbook.model.inspection.HorseInspectionRepository
import com.example.api.domain.studbook.model.inspection.InvalidMicrochipNumber
import com.example.api.domain.studbook.model.inspection.MicrochipNumber
import com.example.api.domain.studbook.model.inspection.ParentageDetermination
import com.example.api.domain.studbook.service.horse.ensurePedigreeRegistrationNumberAvailable
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.binding
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.toResultOr
import java.time.LocalDate
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

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

    /** 血統登録番号が既にこの世界の他の軽種馬に採番済み。 */
    data class RegistrationNumberAlreadyTaken(val registrationNumber: String) :
        RegisterInStudBookUseCaseError

    /** マイクロチップ番号が 15 桁の数字でない。 */
    data object InvalidMicrochipNumber : RegisterInStudBookUseCaseError

    /** 生産者名がブランク。 */
    data object BlankBreeder : RegisterInStudBookUseCaseError

    /** 父として指定された軽種馬が存在しない。 */
    data class SireNotFound(val sireId: UUID) : RegisterInStudBookUseCaseError

    /** 母として指定された軽種馬が存在しない。 */
    data class DamNotFound(val damId: UUID) : RegisterInStudBookUseCaseError

    /**
     * 生成ファクトリ [BloodHorse.create] の前提条件違反を application 層エラーに wrap したもの。
     *
     * 個別バリアント（父が雄でない・品種不整合など）は [RegisterInStudBookError] を参照する。
     */
    data class PreconditionViolated(val cause: RegisterInStudBookError) :
        RegisterInStudBookUseCaseError
}

/**
 * 血統登録ユースケース。
 *
 * 境界の生入力を VO に変換し（不正なら検証エラー）、父・母を [BloodHorseRepository] で引き当て、生成ファクトリ [BloodHorse.create]
 * で前提条件（父=雄・母=雌・DNA 親子整合・品種整合・毛色整合）を検証してから、誕生した [BloodHorse] を 永続化する。Controller
 * 層は本クラスのみに依存し、ドメインの生成経路の詳細は知らない。
 *
 * 血統登録番号の一意性は既存レコード集合への問い合わせを要する集合制約で、集約の構築時不変条件では完結しない ため、ドメインサービス
 * [ensurePedigreeRegistrationNumberAvailable] が引き当てる（ADR-0022）。
 *
 * @return 登録された [RegisteredBloodHorse]、または業務ルール違反を表す [RegisterInStudBookUseCaseError]
 */
@Service
class RegisterInStudBookUseCase(
    private val bloodHorseRepository: BloodHorseRepository,
    private val horseInspectionRepository: HorseInspectionRepository,
) {
    @Transactional
    operator fun invoke(
        actor: Actor,
        command: Command<RegisterInStudBookCommand>,
    ): Result<RegisteredBloodHorse, RegisterInStudBookUseCaseError> = binding {
        val input = command.payload

        val registrationNumber = availableRegistrationNumber(actor, input.registrationNumber).bind()
        val microchipNumber =
            MicrochipNumber.create(input.microchipNumber)
                .mapError { _: InvalidMicrochipNumber ->
                    RegisterInStudBookUseCaseError.InvalidMicrochipNumber
                }
                .bind()
        val breeder =
            Breeder.create(input.breeder)
                .mapError { _: BlankBreeder -> RegisterInStudBookUseCaseError.BlankBreeder }
                .bind()

        // 父・母を 1 回の一括 lookup で引き当てる（逐次往復と sireId==damId の二重取得を避ける）。
        val sireId = BloodHorseId(input.sireId)
        val damId = BloodHorseId(input.damId)
        val found = bloodHorseRepository.findAllById(actor.worldId, setOf(sireId, damId))
        val sire =
            found[sireId]
                .toResultOr { RegisterInStudBookUseCaseError.SireNotFound(input.sireId) }
                .bind()
        val dam =
            found[damId]
                .toResultOr { RegisterInStudBookUseCaseError.DamNotFound(input.damId) }
                .bind()

        val entry =
            StudBookEntry(
                sex = input.sex,
                coatColor = input.coatColor,
                breedType = input.breedType,
                dateOfBirth = DateOfBirth(input.dateOfBirth),
                breeder = breeder,
            )

        // 審査をメモリ内で組み立てる。前提条件検証（BloodHorse.create）を通った後にのみ永続化し、
        // 業務ルール違反での却下時に孤児レコードが残るのを防ぐ。
        val inspection =
            HorseInspection.create(
                microchipNumber = microchipNumber,
                parentage = ParentageDetermination.ByDna(input.dnaParentage),
            )

        val bloodHorse =
            BloodHorse.create(sire, dam, entry, inspection, registrationNumber)
                .mapError { RegisterInStudBookUseCaseError.PreconditionViolated(it) }
                .bind()

        // 審査と軽種馬の 2 集約書き込みは invoke の @Transactional 境界内で原子的に行う（#483）。
        horseInspectionRepository.save(actor.worldId, inspection)
        val saved =
            bloodHorseRepository.save(actor.worldId, bloodHorse).getOrElse {
                error("新規の軽種馬の保存で楽観ロック競合はありえない: id=${bloodHorse.id.value}")
            }
        RegisteredBloodHorse(saved, inspection)
    }

    /** 血統登録番号を VO 検証し、原簿の中で未使用であること（集合制約）まで確かめて返す。 */
    private fun availableRegistrationNumber(
        actor: Actor,
        rawRegistrationNumber: String,
    ): Result<PedigreeRegistrationNumber, RegisterInStudBookUseCaseError> = binding {
        val registrationNumber =
            PedigreeRegistrationNumber.create(rawRegistrationNumber)
                .mapError { _: BlankPedigreeRegistrationNumber ->
                    RegisterInStudBookUseCaseError.InvalidRegistrationNumber
                }
                .bind()
        ensurePedigreeRegistrationNumberAvailable(
                actor.worldId,
                registrationNumber,
                bloodHorseRepository,
            )
            .mapError {
                RegisterInStudBookUseCaseError.RegistrationNumberAlreadyTaken(it.number.value)
            }
            .bind()
        registrationNumber
    }
}
