package com.example.api.application.horseracing.breeding

import com.example.api.domain.horseracing.model.breeding.BreedingResult
import com.example.api.domain.horseracing.model.breeding.BreedingResultId
import com.example.api.domain.horseracing.model.breeding.BreedingResultRepository
import com.example.api.domain.horseracing.model.breeding.FoalingOutcome
import com.example.api.domain.shared.Command
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.binding
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.toResultOr
import java.util.UUID
import org.springframework.stereotype.Service

/**
 * 分娩結果報告ユースケースの入力コマンド。
 *
 * 繁殖成績報告書の「分娩結果」欄に相当する境界の入力。報告対象の繁殖成績を ID で参照し、報告する帰結を ドメインの [FoalingOutcome]
 * で受け取る（生産＝産駒ありか、産駒なしの各区分か）。
 *
 * @property breedingResultId 報告対象の繁殖成績ID
 * @property outcome 報告する分娩結果
 */
data class ReportFoalingCommand(val breedingResultId: UUID, val outcome: FoalingOutcome)

/** 分娩結果報告時に発生しうる業務ルール違反。 */
sealed interface ReportFoalingUseCaseError {
    /** 報告対象として指定された繁殖成績が存在しない。 */
    data class BreedingResultNotFound(val breedingResultId: UUID) : ReportFoalingUseCaseError

    /**
     * 既に分娩結果が報告済みの繁殖成績へ重ねて報告しようとした。
     *
     * 分娩結果の報告は種付年ごとに一度だけ行えるドメインイベントであり、二重報告は不変条件違反。
     *
     * @property current 既に報告されている分娩結果
     */
    data class AlreadyReported(val current: FoalingOutcome) : ReportFoalingUseCaseError
}

/**
 * 分娩結果報告ユースケース。
 *
 * 報告対象の繁殖成績を [BreedingResultRepository] で引き当て、集約の [BreedingResult.recordFoaling] で
 * 分娩結果を確定（二重報告は不変条件違反）してから、報告済みの新インスタンスを永続化する。Controller 層は 本クラスのみに依存する。
 *
 * @return 報告済みの [BreedingResult]、または業務ルール違反を表す [ReportFoalingUseCaseError]
 */
@Service
class ReportFoalingUseCase(private val breedingResultRepository: BreedingResultRepository) {
    operator fun invoke(
        command: Command<ReportFoalingCommand>
    ): Result<BreedingResult, ReportFoalingUseCaseError> = binding {
        val input = command.payload

        val breedingResult =
            breedingResultRepository
                .findById(BreedingResultId(input.breedingResultId))
                .toResultOr {
                    ReportFoalingUseCaseError.BreedingResultNotFound(input.breedingResultId)
                }
                .bind()

        val reported =
            breedingResult
                .recordFoaling(input.outcome)
                .mapError { ReportFoalingUseCaseError.AlreadyReported(it.current) }
                .bind()

        breedingResultRepository.save(reported)
    }
}
