package com.example.api.controller.breeding

import com.example.api.domain.studbook.model.breeding.CoveringReport
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate
import java.util.UUID

/**
 * 種付成績報告リソースの表現（HTTP 契約）。
 *
 * 種付成績報告に対する操作（提出の Create）は [AIP-133](https://google.aip.dev/133) に倣い一律で
 * このリソース表現全体を返す。報告書の内容（雌馬ごとの明細・総括表）は繁殖成績リソースから導出できるため 持たず、提出の事実のみを表す。
 *
 * @property id 種付成績報告の生 UUID
 * @property stallionBreedingRegistrationId 提出した種牡馬の繁殖登録の生 UUID
 * @property coveringYear 種付年（報告対象年）
 * @property submittedOn 提出日（日本の暦日）
 * @property submittedLate 提出が期限（種付年の当年9/30）超過だったか
 */
@Schema(description = "種付成績報告リソースの表現")
data class CoveringReportResponse(
    val id: UUID,
    val stallionBreedingRegistrationId: UUID,
    val coveringYear: Int,
    val submittedOn: LocalDate,
    val submittedLate: Boolean,
)

/** [CoveringReport] を種付成績報告リソースの表現へ変換する。 */
fun CoveringReport.toResponse(): CoveringReportResponse =
    CoveringReportResponse(
        id = id.value,
        stallionBreedingRegistrationId = stallionRegistrationId.value,
        coveringYear = coveringYear.value,
        submittedOn = submittedOn,
        submittedLate = submittedLate,
    )
