package com.example.api.application.horseracing.breeding

import com.example.api.domain.horseracing.model.breeding.BreedingRegistrationId
import com.example.api.domain.horseracing.model.breeding.BreedingRegistrationRepository
import com.example.api.domain.horseracing.model.breeding.BreedingResult
import com.example.api.domain.horseracing.model.breeding.BreedingResultRepository
import com.example.api.domain.horseracing.model.breeding.NotBroodmareForUncovered
import com.example.api.domain.shared.Command
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.binding
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.toResultOr
import java.time.Year
import java.util.UUID
import org.springframework.stereotype.Service

/**
 * 種付せず（種付しなかった年次成績）記録ユースケースの入力コマンド。
 *
 * 繁殖成績報告書の「種付せず」区分に相当する境界の生入力。種付（配合相手・種付日・証明書）を伴わないため、 対象の繁殖牝馬の繁殖登録IDと、種付しなかった繁殖年だけを
 * 受け取る。種付した年と異なり繁殖年は種付日から導出できないため、繁殖年を明示的に受け取る。
 *
 * @property breedingRegistrationId 種付せずの記録対象の繁殖牝馬の繁殖登録ID
 * @property breedingYear 種付しなかった繁殖年
 */
data class RecordUncoveredCommand(val breedingRegistrationId: UUID, val breedingYear: Year)

/** 種付せず記録時に発生しうる業務ルール違反。 */
sealed interface RecordUncoveredUseCaseError {
    /** 記録対象として指定された繁殖牝馬の繁殖登録が存在しない。 */
    data class BreedingRegistrationNotFound(val breedingRegistrationId: UUID) :
        RecordUncoveredUseCaseError

    /**
     * 生成ファクトリ [BreedingResult.createUncovered] の前提条件違反を application 層エラーに wrap したもの。
     *
     * 種付せずの記録は対象の登録ロールが繁殖牝馬であることを前提とする（[NotBroodmareForUncovered]）。
     */
    data class PreconditionViolated(val cause: NotBroodmareForUncovered) :
        RecordUncoveredUseCaseError
}

/**
 * 種付せず（種付しなかった年次成績）記録ユースケース。
 *
 * 対象の繁殖牝馬の繁殖登録を Repository で引き当て、生成ファクトリ [BreedingResult.createUncovered] で前提条件
 * （登録ロールが繁殖牝馬であること）を検証してから、その年の終端の繁殖成績（[BreedingResult]）を永続化する。
 * 種付記録（[RecordCoveringUseCase]）と対称だが、配合相手の種牡馬は伴わない。Controller 層は本クラスのみに依存し、 ドメインの生成経路の詳細は知らない。
 *
 * @return 起こされた種付せずの [BreedingResult]、または業務ルール違反を表す [RecordUncoveredUseCaseError]
 */
@Service
class RecordUncoveredUseCase(
    private val breedingRegistrationRepository: BreedingRegistrationRepository,
    private val breedingResultRepository: BreedingResultRepository,
) {
    operator fun invoke(
        command: Command<RecordUncoveredCommand>
    ): Result<BreedingResult, RecordUncoveredUseCaseError> = binding {
        val input = command.payload

        val broodmareRegistration =
            breedingRegistrationRepository
                .findById(BreedingRegistrationId(input.breedingRegistrationId))
                .toResultOr {
                    RecordUncoveredUseCaseError.BreedingRegistrationNotFound(
                        input.breedingRegistrationId
                    )
                }
                .bind()

        val breedingResult =
            BreedingResult.createUncovered(broodmareRegistration, input.breedingYear)
                .mapError { RecordUncoveredUseCaseError.PreconditionViolated(it) }
                .bind()

        breedingResultRepository.save(breedingResult)
    }
}
