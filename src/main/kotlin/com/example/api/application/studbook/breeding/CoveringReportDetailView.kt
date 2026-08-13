package com.example.api.application.studbook.breeding

import com.example.api.domain.studbook.model.breeding.CoveringReportDeadline
import java.time.LocalDate
import java.time.Year
import java.util.UUID
import org.jmolecules.architecture.cqrs.QueryModel

/**
 * 種付成績報告の単体照会の読み取りモデル（軽量 CQRS / L2。ADR-0031）。
 *
 * 書き込み集約 [com.example.api.domain.studbook.model.breeding.CoveringReport] を経由せず、
 * `studbook.covering_report` から直接組む平坦な DTO。集約と同じく提出の事実のみを持ち、報告書の内容
 * （雌馬ごとの明細・総括表）は繁殖成績から導出できるため保持しない（#540）。
 *
 * @property id 種付成績報告の生 UUID
 * @property stallionBreedingRegistrationId 提出した種牡馬の繁殖登録の生 UUID
 * @property coveringYear 種付年（報告対象年）
 * @property submittedOn 提出日（日本の暦日）
 */
@QueryModel
data class CoveringReportDetailView(
    val id: UUID,
    val stallionBreedingRegistrationId: UUID,
    val coveringYear: Int,
    val submittedOn: LocalDate,
) {
    /**
     * 提出が期限（種付年の当年9/30、[CoveringReportDeadline]）超過だったか。
     *
     * 集約 [com.example.api.domain.studbook.model.breeding.CoveringReport] の同名プロパティと同じく保存しない
     * 導出値で、読み取り経路でも同じドメインの期限定義から算出する（列を増やして二重管理しない）。提出で初めて
     * リソースが生まれる（insert-only）ため、繁殖成績側と違って未提出の状態は無く nullable にならない。
     */
    val submittedLate: Boolean
        get() = CoveringReportDeadline.of(Year.of(coveringYear)).isMissedBy(submittedOn)
}
