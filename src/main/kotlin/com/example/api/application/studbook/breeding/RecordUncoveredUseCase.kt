package com.example.api.application.studbook.breeding

import com.example.api.domain.shared.Command
import com.example.api.domain.studbook.model.breeding.BreedingRegistrationId
import com.example.api.domain.studbook.model.breeding.BreedingRegistrationRepository
import com.example.api.domain.studbook.model.breeding.BreedingResult
import com.example.api.domain.studbook.model.breeding.BreedingResultRepository
import com.example.api.domain.studbook.model.breeding.RecordUncoveredError
import com.example.api.domain.studbook.service.breeding.recordUncovered
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
     * ドメインサービス recordUncovered の前提条件違反を application 層エラーに wrap したもの。
     *
     * 個別バリアント（登録ロールが繁殖牝馬でない・同一繁殖年の重複）は [RecordUncoveredError] を参照する。
     */
    data class PreconditionViolated(val cause: RecordUncoveredError) : RecordUncoveredUseCaseError
}

/**
 * 種付せず（種付しなかった年次成績）記録ユースケース。
 *
 * 対象の繁殖牝馬の繁殖登録を Repository で引き当て、ドメインサービス recordUncovered を呼ぶ。サービスは前提条件 （登録ロールが繁殖牝馬であること・「繁殖牝馬 ×
 * 繁殖年」で一意であること）を検証してから、その年の終端の繁殖成績 （[BreedingResult]）を起こす。一意性の判定に要する同年の既存成績の引き当てはサービスが繁殖成績ポートを介して
 * 行うため、本ユースケースは生成された成績を永続化するだけでよい。種付記録（[RecordCoveringUseCase]）と対称だが、 配合相手の種牡馬は伴わない。Controller
 * 層は本クラスのみに依存し、ドメインの生成経路の詳細は知らない。
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
            recordUncovered(broodmareRegistration, input.breedingYear, breedingResultRepository)
                .mapError { RecordUncoveredUseCaseError.PreconditionViolated(it) }
                .bind()

        breedingResultRepository.save(breedingResult)
    }
}
