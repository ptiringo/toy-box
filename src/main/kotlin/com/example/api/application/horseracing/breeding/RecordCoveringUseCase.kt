package com.example.api.application.horseracing.breeding

import com.example.api.domain.horseracing.model.breeding.BreedingRegistrationId
import com.example.api.domain.horseracing.model.breeding.BreedingRegistrationRepository
import com.example.api.domain.horseracing.model.breeding.BreedingResult
import com.example.api.domain.horseracing.model.breeding.BreedingResultRepository
import com.example.api.domain.horseracing.model.breeding.CoveringCertificateNumber
import com.example.api.domain.horseracing.model.breeding.RecordCoveringError
import com.example.api.domain.horseracing.model.horse.bloodhorse.BloodHorseId
import com.example.api.domain.horseracing.model.horse.bloodhorse.BloodHorseRepository
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
 * を通して検証する。繁殖登録・種牡馬は既存の登録IDで参照する。
 *
 * @property breedingRegistrationId 種付対象の繁殖牝馬の繁殖登録ID
 * @property stallionId 配合相手の種牡馬（雄の軽種馬）の軽種馬ID
 * @property coveringDate 種付日
 * @property certificateNumber 種付の事実を証明する種付証明書の番号
 */
data class RecordCoveringCommand(
    val breedingRegistrationId: UUID,
    val stallionId: UUID,
    val coveringDate: LocalDate,
    val certificateNumber: String,
)

/** 種付記録時に発生しうる業務ルール違反。 */
sealed interface RecordCoveringUseCaseError {
    /** 種付証明書番号がブランク。 */
    data object InvalidCertificateNumber : RecordCoveringUseCaseError

    /** 種付対象として指定された繁殖登録が存在しない。 */
    data class BreedingRegistrationNotFound(val breedingRegistrationId: UUID) :
        RecordCoveringUseCaseError

    /** 配合相手として指定された種牡馬（軽種馬）が存在しない。 */
    data class StallionNotFound(val stallionId: UUID) : RecordCoveringUseCaseError

    /**
     * 生成ファクトリ [BreedingResult.create] の前提条件違反を application 層エラーに wrap したもの。
     *
     * 個別バリアント（種牡馬が雄でない等）は [RecordCoveringError] を参照する。
     */
    data class PreconditionViolated(val cause: RecordCoveringError) : RecordCoveringUseCaseError
}

/**
 * 種付記録ユースケース。
 *
 * 境界の生入力を VO に変換し（不正なら検証エラー）、繁殖登録と種牡馬を Repository で引き当て、生成ファクトリ [BreedingResult.create]
 * で前提条件（種牡馬が雄であること）を検証してから、起こした繁殖成績（[BreedingResult]）の年次 レコードを永続化する。Controller
 * 層は本クラスのみに依存し、ドメインの生成経路の詳細は知らない。
 *
 * @return 起こされた [BreedingResult]、または業務ルール違反を表す [RecordCoveringUseCaseError]
 */
@Service
class RecordCoveringUseCase(
    private val breedingRegistrationRepository: BreedingRegistrationRepository,
    private val bloodHorseRepository: BloodHorseRepository,
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

        val breedingRegistration =
            breedingRegistrationRepository
                .findById(BreedingRegistrationId(input.breedingRegistrationId))
                .toResultOr {
                    RecordCoveringUseCaseError.BreedingRegistrationNotFound(
                        input.breedingRegistrationId
                    )
                }
                .bind()

        val stallion =
            bloodHorseRepository
                .findById(BloodHorseId(input.stallionId))
                .toResultOr { RecordCoveringUseCaseError.StallionNotFound(input.stallionId) }
                .bind()

        val breedingResult =
            BreedingResult.create(
                    breedingRegistration,
                    stallion,
                    input.coveringDate,
                    certificateNumber,
                )
                .mapError { RecordCoveringUseCaseError.PreconditionViolated(it) }
                .bind()

        breedingResultRepository.save(breedingResult)
    }
}
