package com.example.api.application.studbook.breeding

import com.example.api.domain.shared.Command
import com.example.api.domain.studbook.model.breeding.BreedingRegion
import com.example.api.domain.studbook.model.breeding.BreedingRegistrationId
import com.example.api.domain.studbook.model.breeding.BreedingRegistrationRepository
import com.example.api.domain.studbook.model.breeding.BreedingResult
import com.example.api.domain.studbook.model.breeding.BreedingResultRepository
import com.example.api.domain.studbook.model.breeding.CoveringCertificateNumber
import com.example.api.domain.studbook.model.breeding.RecordCoveringError
import com.example.api.domain.studbook.model.breeding.StudCertificate
import com.example.api.domain.studbook.model.breeding.StudCertificateNumber
import com.example.api.domain.studbook.model.breeding.ValidityPeriod
import com.example.api.domain.studbook.service.breeding.recordCovering
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.binding
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.toResultOr
import java.time.LocalDate
import java.util.UUID
import org.springframework.stereotype.Service

/**
 * 種付記録ユースケースの入力コマンド。
 *
 * 繁殖成績報告書の「種付」欄に相当する境界の生入力。VO で表す項目（種付証明書番号・種付場所・種畜証明書）は素の値で 受け取り、ユースケース内で各 VO の `create`
 * を通して検証する。繁殖牝馬・種牡馬はいずれも既存の繁殖登録IDで参照する（種牡馬も繁殖登録の対象）。
 *
 * @property breedingRegistrationId 種付対象の繁殖牝馬の繁殖登録ID
 * @property stallionRegistrationId 配合相手の種牡馬の繁殖登録ID
 * @property coveringDate 種付日
 * @property coveringPlace 種付が行われた場所（有効区域の整合検証に用いる）
 * @property certificateNumber 種付の事実を証明する種付証明書の番号
 * @property studCertificate 種牡馬の種畜証明書（種付の有効性検証の与件）
 */
data class RecordCoveringCommand(
    val breedingRegistrationId: UUID,
    val stallionRegistrationId: UUID,
    val coveringDate: LocalDate,
    val coveringPlace: String,
    val certificateNumber: String,
    val studCertificate: StudCertificateInput,
)

/**
 * 種畜証明書の入力（[RecordCoveringCommand.studCertificate]）。各項目は素の値で受け取り、ユースケースが VO 検証する。
 *
 * @property number 種畜証明書番号
 * @property validRegions 有効区域名（1 つ以上）
 * @property validPeriodStart 有効期間の起点（当日を含む）
 * @property validPeriodEnd 有効期間の終点（当日を含む）
 */
data class StudCertificateInput(
    val number: String,
    val validRegions: List<String>,
    val validPeriodStart: LocalDate,
    val validPeriodEnd: LocalDate,
)

/** 種付記録時に発生しうる業務ルール違反。 */
sealed interface RecordCoveringUseCaseError {
    /** 種付証明書番号がブランク。 */
    data object InvalidCertificateNumber : RecordCoveringUseCaseError

    /** 種付場所がブランク。 */
    data object InvalidCoveringPlace : RecordCoveringUseCaseError

    /** 種畜証明書番号がブランク。 */
    data object InvalidStudCertificateNumber : RecordCoveringUseCaseError

    /** 種畜証明書の有効区域名のいずれかがブランク。 */
    data object InvalidValidRegion : RecordCoveringUseCaseError

    /** 種畜証明書の有効期間が不正（終点が起点より前）。 */
    data object InvalidValidityPeriod : RecordCoveringUseCaseError

    /** 種畜証明書の有効区域が 1 つも指定されていない。 */
    data object EmptyValidRegions : RecordCoveringUseCaseError

    /** 種付対象として指定された繁殖牝馬の繁殖登録が存在しない。 */
    data class BreedingRegistrationNotFound(val breedingRegistrationId: UUID) :
        RecordCoveringUseCaseError

    /** 配合相手として指定された種牡馬の繁殖登録が存在しない。 */
    data class StallionRegistrationNotFound(val stallionRegistrationId: UUID) :
        RecordCoveringUseCaseError

    /**
     * ドメインサービス recordCovering の前提条件違反を application 層エラーに wrap したもの。
     *
     * 個別バリアント（登録ロールが繁殖牝馬／種牡馬でない・同一繁殖年の重複など）は [RecordCoveringError] を参照する。
     */
    data class PreconditionViolated(val cause: RecordCoveringError) : RecordCoveringUseCaseError
}

/**
 * 種付記録ユースケース。
 *
 * 境界の生入力を VO に変換し（不正なら検証エラー）、繁殖牝馬・種牡馬の繁殖登録を Repository で引き当てて、 ドメインサービス recordCovering
 * を呼ぶ。サービスは前提条件（登録ロール・「繁殖牝馬 × 繁殖年」で一意であること）を
 * 検証してから繁殖成績（[BreedingResult]）の年次レコードを起こす。一意性の判定に要する同年の既存成績の引き当ては
 * サービスが繁殖成績ポートを介して行うため、本ユースケースは生成された成績を永続化するだけでよい。 Controller 層は本クラスのみに依存し、ドメインの生成経路の詳細は知らない。
 *
 * @return 起こされた [BreedingResult]、または業務ルール違反を表す [RecordCoveringUseCaseError]
 */
@Service
class RecordCoveringUseCase(
    private val breedingRegistrationRepository: BreedingRegistrationRepository,
    private val breedingResultRepository: BreedingResultRepository,
) {
    operator fun invoke(
        command: Command<RecordCoveringCommand>
    ): Result<BreedingResult, RecordCoveringUseCaseError> = binding {
        val input = command.payload

        val certificateNumber =
            CoveringCertificateNumber.create(input.certificateNumber)
                .mapError { RecordCoveringUseCaseError.InvalidCertificateNumber }
                .bind()

        val coveringPlace =
            BreedingRegion.create(input.coveringPlace)
                .mapError { RecordCoveringUseCaseError.InvalidCoveringPlace }
                .bind()

        val studCertificate = buildStudCertificate(input.studCertificate).bind()

        val broodmareRegistration =
            breedingRegistrationRepository
                .findById(BreedingRegistrationId(input.breedingRegistrationId))
                .toResultOr {
                    RecordCoveringUseCaseError.BreedingRegistrationNotFound(
                        input.breedingRegistrationId
                    )
                }
                .bind()

        val stallionRegistration =
            breedingRegistrationRepository
                .findById(BreedingRegistrationId(input.stallionRegistrationId))
                .toResultOr {
                    RecordCoveringUseCaseError.StallionRegistrationNotFound(
                        input.stallionRegistrationId
                    )
                }
                .bind()

        val breedingResult =
            recordCovering(
                    broodmareRegistration,
                    stallionRegistration,
                    input.coveringDate,
                    certificateNumber,
                    breedingResultRepository,
                    studCertificate,
                    coveringPlace,
                )
                .mapError { RecordCoveringUseCaseError.PreconditionViolated(it) }
                .bind()

        breedingResultRepository.save(breedingResult)
    }

    /** 種畜証明書の生入力を各 VO 検証して [StudCertificate] を組み立てる。形式不正は 400 系の入力エラーにマップする。 */
    private fun buildStudCertificate(
        input: StudCertificateInput
    ): Result<StudCertificate, RecordCoveringUseCaseError> = binding {
        val number =
            StudCertificateNumber.create(input.number)
                .mapError { RecordCoveringUseCaseError.InvalidStudCertificateNumber }
                .bind()

        val validRegions = mutableSetOf<BreedingRegion>()
        for (region in input.validRegions) {
            validRegions.add(
                BreedingRegion.create(region)
                    .mapError { RecordCoveringUseCaseError.InvalidValidRegion }
                    .bind()
            )
        }

        val validityPeriod =
            ValidityPeriod.create(input.validPeriodStart, input.validPeriodEnd)
                .mapError { RecordCoveringUseCaseError.InvalidValidityPeriod }
                .bind()

        StudCertificate.create(number, validRegions, validityPeriod)
            .mapError { RecordCoveringUseCaseError.EmptyValidRegions }
            .bind()
    }
}
