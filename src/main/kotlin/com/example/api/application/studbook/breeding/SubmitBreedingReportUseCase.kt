package com.example.api.application.studbook.breeding

import com.example.api.domain.shared.Actor
import com.example.api.domain.shared.Command
import com.example.api.domain.shared.UpdateConflict
import com.example.api.domain.studbook.model.breeding.BreedingReportDeadline
import com.example.api.domain.studbook.model.breeding.BreedingResult
import com.example.api.domain.studbook.model.breeding.BreedingResultId
import com.example.api.domain.studbook.model.breeding.BreedingResultRepository
import com.example.api.domain.studbook.model.breeding.SubmitBreedingReportError
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.binding
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.toResultOr
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 繁殖成績報告提出ユースケースの入力コマンド。
 *
 * 繁殖成績報告書（様式第14号）の年次提出に相当する境界の入力。提出対象の繁殖成績を ID で参照する。提出日時は ペイロードでは受けず、封筒
 * [Command.issuedAt]（発生時刻メタデータ）を用いる。
 *
 * @property breedingResultId 提出対象の繁殖成績ID
 */
data class SubmitBreedingReportCommand(val breedingResultId: UUID)

/** 繁殖成績報告提出時に発生しうる業務ルール違反。 */
sealed interface SubmitBreedingReportUseCaseError {
    /** 提出対象として指定された繁殖成績が存在しない。 */
    data class BreedingResultNotFound(val breedingResultId: UUID) : SubmitBreedingReportUseCaseError

    /**
     * ドメインの前提条件違反（分娩結果が未確定、または既に提出済み）。
     *
     * @property cause 前提条件違反の内容
     */
    data class PreconditionViolated(val cause: SubmitBreedingReportError) :
        SubmitBreedingReportUseCaseError

    /**
     * 読み取りから提出確定までの間に、対象の繁殖成績が他の更新と競合した。
     *
     * 楽観ロック（読み取り時点の version との不一致）による検出。最新の状態を取得して再操作すれば解消しうる。
     *
     * @property breedingResultId 競合した繁殖成績ID
     */
    data class ConcurrentModification(val breedingResultId: UUID) : SubmitBreedingReportUseCaseError
}

/**
 * 繁殖成績報告提出ユースケース。
 *
 * 提出対象の繁殖成績を [BreedingResultRepository] で引き当て、[Command.issuedAt] を
 * [BreedingReportDeadline.submissionDateOf] で日本の暦日（提出日）に写してから、集約の [BreedingResult.submitReport]
 * で提出を記録し、楽観ロック付きで更新する（競合は
 * [SubmitBreedingReportUseCaseError.ConcurrentModification]）。期限（翌年5/31）超過でも拒否せず、超過は
 * 集約が導出する事実として残る（登録規程第25条ただし書き。#455）。Controller 層は本クラスのみに依存する。
 *
 * @return 提出済みの [BreedingResult]、または業務ルール違反を表す [SubmitBreedingReportUseCaseError]
 */
@Service
class SubmitBreedingReportUseCase(private val breedingResultRepository: BreedingResultRepository) {
    @Transactional
    operator fun invoke(
        actor: Actor,
        command: Command<SubmitBreedingReportCommand>,
    ): Result<BreedingResult, SubmitBreedingReportUseCaseError> = binding {
        val input = command.payload

        val breedingResult =
            breedingResultRepository
                .findById(actor.worldId, BreedingResultId(input.breedingResultId))
                .toResultOr {
                    SubmitBreedingReportUseCaseError.BreedingResultNotFound(input.breedingResultId)
                }
                .bind()

        val submitted =
            breedingResult
                .submitReport(BreedingReportDeadline.submissionDateOf(command.issuedAt))
                .mapError { SubmitBreedingReportUseCaseError.PreconditionViolated(it) }
                .bind()

        breedingResultRepository
            .save(actor.worldId, submitted)
            .mapError { _: UpdateConflict ->
                SubmitBreedingReportUseCaseError.ConcurrentModification(input.breedingResultId)
            }
            .bind()
    }
}
