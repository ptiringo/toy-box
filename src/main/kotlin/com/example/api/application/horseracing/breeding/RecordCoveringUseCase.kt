package com.example.api.application.horseracing.breeding

import com.example.api.domain.horseracing.model.breeding.BreedingRegistrationId
import com.example.api.domain.horseracing.model.breeding.BreedingRegistrationRepository
import com.example.api.domain.horseracing.model.breeding.BreedingResult
import com.example.api.domain.horseracing.model.breeding.BreedingResultRepository
import com.example.api.domain.horseracing.model.breeding.CoveringCertificateNumber
import com.example.api.domain.horseracing.service.breeding.RecordCoveringError
import com.example.api.domain.horseracing.service.breeding.recordCovering
import com.example.api.domain.shared.Command
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
 * 繁殖成績報告書の「種付」欄に相当する境界の生入力。種付証明書番号は素の文字列で受け取り、ユースケース内で VO の `create`
 * を通して検証する。繁殖牝馬・種牡馬はいずれも既存の繁殖登録IDで参照する（種牡馬も繁殖登録の対象）。
 *
 * @property breedingRegistrationId 種付対象の繁殖牝馬の繁殖登録ID
 * @property stallionRegistrationId 配合相手の種牡馬の繁殖登録ID
 * @property coveringDate 種付日
 * @property certificateNumber 種付の事実を証明する種付証明書の番号
 */
data class RecordCoveringCommand(
    val breedingRegistrationId: UUID,
    val stallionRegistrationId: UUID,
    val coveringDate: LocalDate,
    val certificateNumber: String,
)

/** 種付記録時に発生しうる業務ルール違反。 */
sealed interface RecordCoveringUseCaseError {
    /** 種付証明書番号がブランク。 */
    data object InvalidCertificateNumber : RecordCoveringUseCaseError

    /** 種付対象として指定された繁殖牝馬の繁殖登録が存在しない。 */
    data class BreedingRegistrationNotFound(val breedingRegistrationId: UUID) :
        RecordCoveringUseCaseError

    /** 配合相手として指定された種牡馬の繁殖登録が存在しない。 */
    data class StallionRegistrationNotFound(val stallionRegistrationId: UUID) :
        RecordCoveringUseCaseError

    /**
     * ドメインサービス recordCovering の前提条件違反を application 層エラーに wrap したもの。
     *
     * 個別バリアント（登録ロールが繁殖牝馬／種牡馬でない等）は [RecordCoveringError] を参照する。
     */
    data class PreconditionViolated(val cause: RecordCoveringError) : RecordCoveringUseCaseError
}

/**
 * 種付記録ユースケース。
 *
 * 境界の生入力を VO に変換し（不正なら検証エラー）、繁殖牝馬・種牡馬の繁殖登録を Repository で引き当て、ドメインサービス recordCovering
 * で前提条件（両者の登録ロールが繁殖牝馬・種牡馬であること）を検証してから、起こした繁殖成績 （[BreedingResult]）の年次レコードを永続化する。Controller
 * 層は本クラスのみに依存し、ポートやドメインサービスは知らない。
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
                )
                .mapError { RecordCoveringUseCaseError.PreconditionViolated(it) }
                .bind()

        breedingResultRepository.save(breedingResult)
    }
}
