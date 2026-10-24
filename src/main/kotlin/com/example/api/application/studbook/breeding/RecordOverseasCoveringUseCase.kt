package com.example.api.application.studbook.breeding

import com.example.api.domain.shared.Actor
import com.example.api.domain.shared.Command
import com.example.api.domain.studbook.model.breeding.BlankForeignCoveringProofNumber
import com.example.api.domain.studbook.model.breeding.BreedingRegistrationId
import com.example.api.domain.studbook.model.breeding.BreedingRegistrationRepository
import com.example.api.domain.studbook.model.breeding.BreedingResult
import com.example.api.domain.studbook.model.breeding.BreedingResultRepository
import com.example.api.domain.studbook.model.breeding.ForeignCoveringProofNumber
import com.example.api.domain.studbook.model.breeding.RecordOverseasCoveringError
import com.example.api.domain.studbook.model.horse.bloodhorse.BlankForeignSireName
import com.example.api.domain.studbook.model.horse.bloodhorse.BlankOriginCountry
import com.example.api.domain.studbook.model.horse.bloodhorse.BreedType
import com.example.api.domain.studbook.model.horse.bloodhorse.CoatColor
import com.example.api.domain.studbook.model.horse.bloodhorse.ForeignSire
import com.example.api.domain.studbook.model.horse.bloodhorse.ForeignSireName
import com.example.api.domain.studbook.model.horse.bloodhorse.OriginCountry
import com.example.api.domain.studbook.service.breeding.recordOverseasCovering
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
 * 海外種付記録ユースケースの入力コマンド。
 *
 * 持込馬の経路の入口で、外国で外国種牡馬と行われた種付を記録する（#624 / ADR-0087）。国内種付
 * （[RecordCoveringCommand]）と異なり、父は日本の繁殖登録を持たないため ID ではなく属性で受け取り、種畜証明書・
 * 種付証明書の代わりに種付国の血統登録機関が発行した種付事実の証明の番号を受け取る（登録規程 第8条第1項第4号）。 VO で表す項目は素の値で受け取り、ユースケース内で各 VO の
 * `create` を通して検証する。
 *
 * @property breedingRegistrationId 種付対象の繁殖牝馬の繁殖登録ID
 * @property sire 外国種牡馬
 * @property coveringDate 種付日
 * @property coveringCountry 種付が行われた国
 * @property proofNumber 種付国の血統登録機関が発行した種付事実の証明の番号
 */
data class RecordOverseasCoveringCommand(
    val breedingRegistrationId: UUID,
    val sire: ForeignSireInput,
    val coveringDate: LocalDate,
    val coveringCountry: String,
    val proofNumber: String,
)

/**
 * 外国種牡馬の入力（[RecordOverseasCoveringCommand.sire]）。馬名・登録機関の国は素の値で受け取り、ユースケースが VO 検証する。
 *
 * @property name 馬名（外国の血統登録機関に登録された名）
 * @property studBookCountry 登録している血統登録機関の国
 * @property breedType 品種
 * @property coatColor 毛色
 */
data class ForeignSireInput(
    val name: String,
    val studBookCountry: String,
    val breedType: BreedType,
    val coatColor: CoatColor,
)

/** 海外種付記録時に発生しうる業務ルール違反。 */
sealed interface RecordOverseasCoveringUseCaseError {
    /** 外国種牡馬の馬名がブランク。 */
    data object BlankSireName : RecordOverseasCoveringUseCaseError

    /** 外国種牡馬の登録機関の国がブランク。 */
    data object BlankSireStudBookCountry : RecordOverseasCoveringUseCaseError

    /** 種付国がブランク。 */
    data object BlankCoveringCountry : RecordOverseasCoveringUseCaseError

    /** 種付事実の証明の番号がブランク。 */
    data object BlankProofNumber : RecordOverseasCoveringUseCaseError

    /** 種付対象として指定された繁殖牝馬の繁殖登録が存在しない。 */
    data class BreedingRegistrationNotFound(val breedingRegistrationId: UUID) :
        RecordOverseasCoveringUseCaseError

    /**
     * ドメインサービス recordOverseasCovering の前提条件違反を application 層エラーに wrap したもの。
     *
     * 個別バリアント（登録ロールが繁殖牝馬でない・同一繁殖年の重複）は [RecordOverseasCoveringError] を参照する。
     */
    data class PreconditionViolated(val cause: RecordOverseasCoveringError) :
        RecordOverseasCoveringUseCaseError
}

/**
 * 海外種付記録ユースケース。
 *
 * 境界の生入力を VO に変換し（不正なら検証エラー）、繁殖牝馬の繁殖登録を Repository で引き当てて、ドメインサービス recordOverseasCovering
 * を呼ぶ。サービスは前提条件（登録ロール・「繁殖牝馬 × 繁殖年」で一意であること）を検証してから繁殖成績
 * （[BreedingResult]）の年次レコードを起こす。父は外国種牡馬で日本の繁殖登録を持たないため、国内種付の [RecordCoveringUseCase]
 * と異なり種牡馬の繁殖登録は引き当てない。
 *
 * @return 起こされた [BreedingResult]、または業務ルール違反を表す [RecordOverseasCoveringUseCaseError]
 */
@Service
class RecordOverseasCoveringUseCase(
    private val breedingRegistrationRepository: BreedingRegistrationRepository,
    private val breedingResultRepository: BreedingResultRepository,
) {
    @Transactional
    operator fun invoke(
        actor: Actor,
        command: Command<RecordOverseasCoveringCommand>,
    ): Result<BreedingResult, RecordOverseasCoveringUseCaseError> = binding {
        val input = command.payload

        val sire = buildForeignSire(input.sire).bind()
        val coveringCountry =
            OriginCountry.create(input.coveringCountry)
                .mapError { _: BlankOriginCountry ->
                    RecordOverseasCoveringUseCaseError.BlankCoveringCountry
                }
                .bind()
        val proofNumber =
            ForeignCoveringProofNumber.create(input.proofNumber)
                .mapError { _: BlankForeignCoveringProofNumber ->
                    RecordOverseasCoveringUseCaseError.BlankProofNumber
                }
                .bind()

        val broodmareRegistration =
            breedingRegistrationRepository
                .findById(actor.worldId, BreedingRegistrationId(input.breedingRegistrationId))
                .toResultOr {
                    RecordOverseasCoveringUseCaseError.BreedingRegistrationNotFound(
                        input.breedingRegistrationId
                    )
                }
                .bind()

        val breedingResult =
            recordOverseasCovering(
                    actor.worldId,
                    broodmareRegistration,
                    sire,
                    input.coveringDate,
                    coveringCountry,
                    proofNumber,
                    breedingResultRepository,
                )
                .mapError { RecordOverseasCoveringUseCaseError.PreconditionViolated(it) }
                .bind()

        breedingResultRepository.save(actor.worldId, breedingResult).getOrElse {
            error("新規の繁殖成績の保存で楽観ロック競合はありえない: id=${breedingResult.id.value}")
        }
    }

    /** 外国種牡馬の生入力を各 VO 検証して [ForeignSire] を組み立てる。形式不正は 400 系の入力エラーにマップする。 */
    private fun buildForeignSire(
        input: ForeignSireInput
    ): Result<ForeignSire, RecordOverseasCoveringUseCaseError> = binding {
        val name =
            ForeignSireName.create(input.name)
                .mapError { _: BlankForeignSireName ->
                    RecordOverseasCoveringUseCaseError.BlankSireName
                }
                .bind()
        val studBookCountry =
            OriginCountry.create(input.studBookCountry)
                .mapError { _: BlankOriginCountry ->
                    RecordOverseasCoveringUseCaseError.BlankSireStudBookCountry
                }
                .bind()
        ForeignSire(name, studBookCountry, input.breedType, input.coatColor)
    }
}
