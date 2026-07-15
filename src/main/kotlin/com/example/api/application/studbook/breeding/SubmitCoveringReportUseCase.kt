package com.example.api.application.studbook.breeding

import com.example.api.application.shared.AuthorizationError
import com.example.api.domain.shared.Actor
import com.example.api.domain.shared.Command
import com.example.api.domain.shared.Permission
import com.example.api.domain.studbook.model.StudbookPermissions
import com.example.api.domain.studbook.model.breeding.BreedingRegistrationId
import com.example.api.domain.studbook.model.breeding.BreedingRegistrationRepository
import com.example.api.domain.studbook.model.breeding.CoveringReport
import com.example.api.domain.studbook.model.breeding.CoveringReportDeadline
import com.example.api.domain.studbook.model.breeding.CoveringReportRepository
import com.example.api.domain.studbook.model.breeding.SubmitCoveringReportError
import com.example.api.domain.studbook.service.breeding.submitCoveringReport
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.binding
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.toResultOr
import java.time.Year
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 種付成績報告提出ユースケースの入力コマンド。
 *
 * 種付成績報告書（様式第13号）の年次提出に相当する境界の入力。提出する種牡馬は既存の繁殖登録IDで参照する。 提出日時はペイロードでは受けず、封筒
 * [Command.issuedAt]（発生時刻メタデータ）を用いる。
 *
 * @property stallionBreedingRegistrationId 提出する種牡馬の繁殖登録ID
 * @property coveringYear 報告対象の種付年
 */
data class SubmitCoveringReportCommand(
    val stallionBreedingRegistrationId: UUID,
    val coveringYear: Int,
)

/** 種付成績報告提出時に発生しうる業務ルール違反。 */
sealed interface SubmitCoveringReportUseCaseError {
    /** 提出者として指定された種牡馬の繁殖登録が存在しない。 */
    data class StallionRegistrationNotFound(val stallionBreedingRegistrationId: UUID) :
        SubmitCoveringReportUseCaseError

    /**
     * ドメインの前提条件違反（登録ロールが種牡馬でない、または同年に提出済み）。
     *
     * @property cause 前提条件違反の内容
     */
    data class PreconditionViolated(val cause: SubmitCoveringReportError) :
        SubmitCoveringReportUseCaseError

    /** 種付成績報告提出に必要な権限を持たない。 */
    data class Forbidden(override val permission: Permission) :
        SubmitCoveringReportUseCaseError, AuthorizationError
}

/**
 * 種付成績報告提出ユースケース。
 *
 * 提出する種牡馬の繁殖登録を [BreedingRegistrationRepository] で引き当て、[Command.issuedAt] を
 * [CoveringReportDeadline.submissionDateOf] で日本の暦日（提出日）に写してから、ドメインサービス [submitCoveringReport]
 * を呼ぶ。サービスは前提条件（登録ロール・「種牡馬 × 種付年」で提出は一度）を検証して
 * から提出記録（[CoveringReport]）を起こす。一意性の判定に要する同年の既存報告の引き当てはサービスが
 * 種付成績報告ポートを介して行うため、本ユースケースは生成された報告を永続化するだけでよい。
 *
 * 期限（当年9/30）超過でも拒否せず、超過は集約が導出する事実として残る（登録規程第25条。#540）。 種付実績の有無は前提にしない（種付 0 件の年の提出も受理する）。Controller
 * 層は本クラスのみに依存する。
 *
 * @return 提出された [CoveringReport]、または業務ルール違反を表す [SubmitCoveringReportUseCaseError]
 */
@Service
class SubmitCoveringReportUseCase(
    private val breedingRegistrationRepository: BreedingRegistrationRepository,
    private val coveringReportRepository: CoveringReportRepository,
) {
    @Transactional
    operator fun invoke(
        actor: Actor,
        command: Command<SubmitCoveringReportCommand>,
    ): Result<CoveringReport, SubmitCoveringReportUseCaseError> {
        val permission = StudbookPermissions.COVERING_REPORT_SUBMIT
        if (!actor.can(permission)) {
            return Err(SubmitCoveringReportUseCaseError.Forbidden(permission))
        }
        return binding {
            val input = command.payload

            val stallionRegistration =
                breedingRegistrationRepository
                    .findById(BreedingRegistrationId(input.stallionBreedingRegistrationId))
                    .toResultOr {
                        SubmitCoveringReportUseCaseError.StallionRegistrationNotFound(
                            input.stallionBreedingRegistrationId
                        )
                    }
                    .bind()

            val coveringReport =
                submitCoveringReport(
                        stallionRegistration,
                        Year.of(input.coveringYear),
                        CoveringReportDeadline.submissionDateOf(command.issuedAt),
                        coveringReportRepository,
                    )
                    .mapError { SubmitCoveringReportUseCaseError.PreconditionViolated(it) }
                    .bind()

            coveringReportRepository.save(coveringReport).getOrElse {
                error("新規の種付成績報告の保存で楽観ロック競合はありえない: id=${coveringReport.id.value}")
            }
        }
    }
}
