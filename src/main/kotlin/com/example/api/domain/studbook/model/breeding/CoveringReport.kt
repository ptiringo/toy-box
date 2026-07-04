package com.example.api.domain.studbook.model.breeding

import com.example.api.domain.shared.Entity
import com.example.api.domain.shared.generateId
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import java.time.LocalDate
import java.time.Year
import java.util.UUID
import org.jmolecules.ddd.annotation.AggregateRoot
import org.jmolecules.ddd.annotation.Identity
import org.jmolecules.ddd.annotation.ValueObject

/** 種付成績報告ID */
@ValueObject @JvmInline value class CoveringReportId(val value: UUID)

/**
 * 種付成績報告の提出（ドメインサービス submitCoveringReport / ファクトリ [CoveringReport.create]）の前提条件違反。
 *
 * 提出の前提は2系統ある。(1) 対象の繁殖登録のロールが種牡馬（STALLION）であること＝単一インスタンスの構築時 不変条件で、ファクトリ [CoveringReport.create]
 * が検証する（[NotStallion]）。(2)「種牡馬 × 種付年」で提出は 一度という集合制約で、既存報告群をまたぐためドメインサービス submitCoveringReport が検証する
 * （[AlreadySubmittedForYear]）。なお期限（当年9/30）超過は前提条件違反ではない（拒否せず受理して記録する。 [CoveringReportDeadline]
 * 参照）。種付実績の有無も前提にしない（種付 0 件の年の提出も受理する。#540）。
 */
sealed interface SubmitCoveringReportError {
    /** 提出対象の繁殖登録のロールが種牡馬（STALLION）でない。ファクトリ [CoveringReport.create] が検証する。 */
    data object NotStallion : SubmitCoveringReportError

    /**
     * 同一種牡馬・同一種付年に既に種付成績報告が提出されている。
     *
     * 年次の報告書の提出は種付年ごとに一度であり、これは単一インスタンスの構築では完結しない集合制約のため、 ドメインサービス submitCoveringReport が検証する。
     *
     * @property coveringYear 重複した種付年
     * @property existingCoveringReportId 既に存在する同年の種付成績報告のID
     */
    data class AlreadySubmittedForYear(
        val coveringYear: Year,
        val existingCoveringReportId: CoveringReportId,
    ) : SubmitCoveringReportError
}

/**
 * 種付成績報告を表す集約ルート。種牡馬×種付年ごとの提出記録。
 *
 * 繁殖登録済みの種牡馬（[BreedingRegistration] のロールが STALLION）について、種付成績報告書（様式第13号）の
 * 年次提出の事実を記録する。報告書の内容（雌馬ごとの明細・総括表）は牝側の年次成績 [BreedingResult]（種牡馬を `Covering.stallionId`
 * で参照）から導出できるため保持せず、提出の事実（提出日）のみを持つ（#540）。 繁殖登録は別集約であり、参照は [BreedingRegistrationId] 経由で表す。
 *
 * 牝側（様式第14号）は年次レコード [BreedingResult] に提出を貼ったが、雄側には先行するライフサイクルが無く、 **提出行為そのものが本集約を生成する**。したがって
 * [submittedOn] は非 null で「未提出の報告」状態は存在せず、
 * 生成後の状態遷移も持たない（insert-only）。期限（当年9/30、[CoveringReportDeadline]）超過の提出は拒否せず 受理し、超過かどうかは
 * [submittedLate] が導出する。
 *
 * ロール＝種牡馬の検証は繁殖登録を引数で受け取る生成ファクトリ [create] がその場で自己検証する（ADR-0014）。 「種牡馬 × 種付年」で一意という集合制約はドメインサービス
 * submitCoveringReport が担う（ADR-0022）。 コンストラクタは private とし、生成は [create]（新規）と [reconstitute]（復元）に限る。
 *
 * @property id 種付成績報告ID（生成時に自動採番）
 * @property stallionRegistrationId この報告を提出した種牡馬の繁殖登録のID
 * @property coveringYear 種付年（報告対象年。当年 9/30 が提出期限）
 * @property submittedOn 提出日（日本の暦日。[CoveringReportDeadline.submissionDateOf] で写した値を渡す）
 */
@AggregateRoot
class CoveringReport
private constructor(
    @field:Identity override val id: CoveringReportId,
    val stallionRegistrationId: BreedingRegistrationId,
    val coveringYear: Year,
    val submittedOn: LocalDate,
    override val version: Long? = null,
) : Entity<CoveringReportId>() {

    /** 提出が期限（当年9/30、[CoveringReportDeadline]）超過だったか。導出値であり保存しない。 */
    val submittedLate: Boolean
        get() = CoveringReportDeadline.of(coveringYear).isMissedBy(submittedOn)

    companion object {
        /**
         * 種牡馬の種付成績報告書（様式第13号）の年次提出を記録し、[CoveringReport] を生成する。
         *
         * 本ファクトリが守るのは「単一の報告インスタンスの構築時不変条件」＝対象の繁殖登録のロールが種牡馬で
         * あること（[SubmitCoveringReportError.NotStallion]）に限る。「種牡馬 × 種付年」で提出は一度という
         * 集合制約は既存報告群をまたぐためドメインサービス submitCoveringReport が担い、本ファクトリはその
         * 検証を経た上で呼び出される。種付実績の有無は前提にしない（種付 0 件の年の提出も受理する）。
         *
         * @param stallionRegistration 提出する種牡馬の繁殖登録（ロールが種牡馬であること）
         * @param coveringYear 報告対象の種付年
         * @param submittedOn 提出日（日本の暦日）
         * @return 提出を記録した [CoveringReport]、またはロールの前提条件違反 [SubmitCoveringReportError.NotStallion]
         */
        fun create(
            stallionRegistration: BreedingRegistration,
            coveringYear: Year,
            submittedOn: LocalDate,
        ): Result<CoveringReport, SubmitCoveringReportError> =
            if (stallionRegistration.role != BreedingRole.STALLION) {
                Err(SubmitCoveringReportError.NotStallion)
            } else {
                Ok(
                    CoveringReport(
                        id = CoveringReportId(generateId()),
                        stallionRegistrationId = stallionRegistration.id,
                        coveringYear = coveringYear,
                        submittedOn = submittedOn,
                    )
                )
            }

        /**
         * 永続化層に保存済みの状態から [CoveringReport] を再構成（リハイドレート）する。
         *
         * 既に [create] を通過して保存された状態の復元であり、前提条件（登録ロール・集合制約）の再検証も ID の
         * 再採番も行わない。永続化アダプター（infrastructure 層）からの復元専用であり、新規生成には [create] を使うこと。
         *
         * @param version DB の version 列の値（楽観ロック）
         */
        fun reconstitute(
            id: CoveringReportId,
            stallionRegistrationId: BreedingRegistrationId,
            coveringYear: Year,
            submittedOn: LocalDate,
            version: Long?,
        ): CoveringReport =
            CoveringReport(id, stallionRegistrationId, coveringYear, submittedOn, version)
    }
}
