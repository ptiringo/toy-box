package com.example.api.controller.breeding

import com.example.api.application.horseracing.breeding.RecordCoveringUseCaseError
import com.example.api.application.horseracing.breeding.ReportFoalingUseCaseError
import com.example.api.controller.problem
import com.example.api.domain.horseracing.model.breeding.BreedingResult
import com.example.api.domain.horseracing.model.breeding.RecordCoveringError
import java.time.LocalDate
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail

/**
 * 繁殖成績リソースの表現（HTTP 契約）。
 *
 * 繁殖成績に対する操作（種付記録の Create、分娩結果報告の `:reportFoaling` カスタムメソッド）は、
 * [AIP-133](https://google.aip.dev/133) / [AIP-136](https://google.aip.dev/136) に倣い一律で
 * このリソース表現全体を返す。種付年は種付日から導出した値を持つ。分娩結果は未報告なら null。
 *
 * @property id 繁殖成績の生 UUID
 * @property breedingRegistrationId 紐づく繁殖登録（繁殖牝馬のロール）の生 UUID
 * @property coveringYear 種付年
 * @property stallionId 種牡馬の生 UUID
 * @property coveringDate 種付日
 * @property certificateNumber 種付証明書番号
 * @property outcome 分娩結果。未報告なら null
 */
data class BreedingResultResponse(
    val id: UUID,
    val breedingRegistrationId: UUID,
    val coveringYear: Int,
    val stallionId: UUID,
    val coveringDate: LocalDate,
    val certificateNumber: String,
    val outcome: FoalingOutcomeResponse?,
)

/** [BreedingResult] を繁殖成績リソースの表現へ変換する。各操作の成功レスポンスはこのリソース表現を一律で返す。 */
fun BreedingResult.toResponse(): BreedingResultResponse =
    BreedingResultResponse(
        id = id.value,
        breedingRegistrationId = breedingRegistrationId.value,
        coveringYear = coveringYear.value,
        stallionId = covering.stallionId.value,
        coveringDate = covering.coveringDate,
        certificateNumber = covering.certificateNumber.value,
        outcome = outcome?.toResponse(),
    )

/**
 * [RecordCoveringUseCaseError] を RFC 9457 (`application/problem+json`) の [ProblemDetail] に変換する。
 *
 * - VO 検証エラーは入力不正として 400 Bad Request
 * - 繁殖登録・種牡馬の不在やドメイン前提条件違反は、整った入力だが意味的に処理できないため 422 Unprocessable Entity
 */
fun RecordCoveringUseCaseError.toProblemDetail(): ProblemDetail =
    when (this) {
        RecordCoveringUseCaseError.InvalidCertificateNumber ->
            problem(
                status = HttpStatus.BAD_REQUEST,
                code = "invalid-covering-certificate-number",
                title = "Invalid covering certificate number",
                detail = "certificate_number は空であってはいけません。",
            )
        is RecordCoveringUseCaseError.BreedingRegistrationNotFound ->
            problem(
                    status = HttpStatus.UNPROCESSABLE_ENTITY,
                    code = "breeding-registration-not-found",
                    title = "Breeding registration not found",
                    detail = "種付対象として指定された繁殖登録が存在しません。",
                )
                .apply { setProperty("breeding_registration_id", breedingRegistrationId) }
        is RecordCoveringUseCaseError.StallionNotFound ->
            problem(
                    status = HttpStatus.UNPROCESSABLE_ENTITY,
                    code = "stallion-not-found",
                    title = "Stallion not found",
                    detail = "配合相手として指定された種牡馬が存在しません。",
                )
                .apply { setProperty("stallion_id", stallionId) }
        is RecordCoveringUseCaseError.PreconditionViolated -> cause.toProblemDetail()
    }

private fun RecordCoveringError.toProblemDetail(): ProblemDetail =
    when (this) {
        RecordCoveringError.StallionNotMale ->
            problem(
                status = HttpStatus.UNPROCESSABLE_ENTITY,
                code = "stallion-not-male",
                title = "Stallion is not male",
                detail = "配合相手として指定された軽種馬が雄ではありません。",
            )
    }

/**
 * [ReportFoalingUseCaseError] を RFC 9457 (`application/problem+json`) の [ProblemDetail] に変換する。
 *
 * - 報告対象（URL パスの繁殖成績）の不在は 404 Not Found
 * - 二重報告（既に分娩結果が報告済み）は状態の競合として 409 Conflict
 */
fun ReportFoalingUseCaseError.toProblemDetail(): ProblemDetail =
    when (this) {
        is ReportFoalingUseCaseError.BreedingResultNotFound ->
            problem(
                    status = HttpStatus.NOT_FOUND,
                    code = "breeding-result-not-found",
                    title = "Breeding result not found",
                    detail = "報告対象として指定された繁殖成績が存在しません。",
                )
                .apply { setProperty("breeding_result_id", breedingResultId) }
        is ReportFoalingUseCaseError.AlreadyReported ->
            problem(
                status = HttpStatus.CONFLICT,
                code = "foaling-already-recorded",
                title = "Foaling already recorded",
                detail = "この繁殖成績には既に分娩結果が報告されています。",
            )
    }
