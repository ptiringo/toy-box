package com.example.api.application.studbook.breeding

import com.example.api.domain.studbook.model.breeding.BreedingReportDeadline
import com.example.api.domain.studbook.model.breeding.FoalingOutcome
import java.time.LocalDate
import java.time.Year
import java.util.UUID
import org.jmolecules.architecture.cqrs.QueryModel

/**
 * 繁殖成績の単体照会の読み取りモデル（軽量 CQRS / L2。ADR-0031）。
 *
 * 書き込み集約 [com.example.api.domain.studbook.model.breeding.BreedingResult] を経由せず、
 * `studbook.breeding_result` から直接組む平坦な DTO。種付（nullable な `Covering`）はフラット化した 4 項目 （[stallionId] /
 * [coveringDate] / [coveringPlace] / [certificateNumber]）で表し、種付せずの年は 4 項目とも null になる。分娩結果 [outcome]
 * は sealed なドメイン型をそのまま持つ（判別子の意味づけを adapter 側で 再実装しないため。wire への公開は adapter が `〜Dto` へ写す。ADR-0007）。
 *
 * @property id 繁殖成績の生 UUID
 * @property breedingRegistrationId 紐づく繁殖登録（繁殖牝馬のロール）の生 UUID
 * @property breedingYear 繁殖年
 * @property stallionId 種牡馬の生 UUID。種付せずの年は null
 * @property coveringDate 種付日。種付せずの年は null
 * @property coveringPlace 種付が行われた場所。種付せずの年、または場所未記録なら null
 * @property certificateNumber 種付証明書番号。種付せずの年は null
 * @property outcome 分娩結果。種付した年で未報告なら null
 * @property reportSubmittedOn 繁殖成績報告書（様式第14号）の提出日。未提出は null
 */
@QueryModel
data class BreedingResultDetailView(
    val id: UUID,
    val breedingRegistrationId: UUID,
    val breedingYear: Int,
    val stallionId: UUID?,
    val coveringDate: LocalDate?,
    val coveringPlace: String?,
    val certificateNumber: String?,
    val outcome: FoalingOutcome?,
    val reportSubmittedOn: LocalDate?,
) {
    /**
     * 提出が期限（繁殖年の翌年5/31、[BreedingReportDeadline]）超過だったか。未提出なら null。
     *
     * 集約 [com.example.api.domain.studbook.model.breeding.BreedingResult] の同名プロパティと同じく保存しない
     * 導出値で、読み取り経路でも同じドメインの期限定義から算出する（列を増やして二重管理しない）。
     */
    val reportSubmittedLate: Boolean?
        get() = reportSubmittedOn?.let {
            BreedingReportDeadline.of(Year.of(breedingYear)).isMissedBy(it)
        }
}
