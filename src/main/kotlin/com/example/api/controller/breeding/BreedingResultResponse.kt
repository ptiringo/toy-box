package com.example.api.controller.breeding

import com.example.api.application.horseracing.breeding.RecordCoveringUseCaseError
import com.example.api.application.horseracing.breeding.RecordUncoveredUseCaseError
import com.example.api.application.horseracing.breeding.ReportFoalingUseCaseError
import com.example.api.controller.problem
import com.example.api.domain.horseracing.model.breeding.BreedingResult
import com.example.api.domain.horseracing.model.breeding.RecordCoveringError
import com.example.api.domain.horseracing.model.breeding.RecordUncoveredError
import java.time.LocalDate
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail

/**
 * 繁殖成績リソースの表現（HTTP 契約）。
 *
 * 繁殖成績に対する操作（種付記録の Create、分娩結果報告の `:reportFoaling` カスタムメソッド）は、
 * [AIP-133](https://google.aip.dev/133) / [AIP-136](https://google.aip.dev/136) に倣い一律で
 * このリソース表現全体を返す。繁殖年は集計・報告の単位で、種付した年は種付日の年と一致する。種付せず（その年に 種付しなかった）の年次成績では種付関連の項目（[stallionId] /
 * [coveringDate] / [certificateNumber]）が null に なり、[outcome] は種付せず区分を持つ。種付した年の分娩結果は未報告なら [outcome]
 * が null。
 *
 * @property id 繁殖成績の生 UUID
 * @property breedingRegistrationId 紐づく繁殖登録（繁殖牝馬のロール）の生 UUID
 * @property breedingYear 繁殖年
 * @property stallionId 種牡馬の生 UUID。種付せずの年は null
 * @property coveringDate 種付日。種付せずの年は null
 * @property certificateNumber 種付証明書番号。種付せずの年は null
 * @property outcome 分娩結果。種付した年で未報告なら null
 */
data class BreedingResultResponse(
    val id: UUID,
    val breedingRegistrationId: UUID,
    val breedingYear: Int,
    val stallionId: UUID?,
    val coveringDate: LocalDate?,
    val certificateNumber: String?,
    val outcome: FoalingOutcomeResponse?,
)

/** [BreedingResult] を繁殖成績リソースの表現へ変換する。各操作の成功レスポンスはこのリソース表現を一律で返す。 */
fun BreedingResult.toResponse(): BreedingResultResponse =
    BreedingResultResponse(
        id = id.value,
        breedingRegistrationId = breedingRegistrationId.value,
        breedingYear = breedingYear.value,
        stallionId = covering?.stallionId?.value,
        coveringDate = covering?.coveringDate,
        certificateNumber = covering?.certificateNumber?.value,
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
        is RecordCoveringUseCaseError.StallionRegistrationNotFound ->
            problem(
                    status = HttpStatus.UNPROCESSABLE_ENTITY,
                    code = "stallion-registration-not-found",
                    title = "Stallion registration not found",
                    detail = "配合相手として指定された種牡馬の繁殖登録が存在しません。",
                )
                .apply { setProperty("stallion_registration_id", stallionRegistrationId) }
        is RecordCoveringUseCaseError.PreconditionViolated -> cause.toProblemDetail()
    }

private fun RecordCoveringError.toProblemDetail(): ProblemDetail =
    when (this) {
        RecordCoveringError.NotBroodmare ->
            problem(
                status = HttpStatus.UNPROCESSABLE_ENTITY,
                code = "not-broodmare",
                title = "Registration is not a broodmare",
                detail = "種付対象として指定された繁殖登録のロールが繁殖牝馬ではありません。",
            )
        RecordCoveringError.NotStallion ->
            problem(
                status = HttpStatus.UNPROCESSABLE_ENTITY,
                code = "not-stallion",
                title = "Registration is not a stallion",
                detail = "配合相手として指定された繁殖登録のロールが種牡馬ではありません。",
            )
        is RecordCoveringError.AlreadyRecordedForYear ->
            problem(
                    status = HttpStatus.CONFLICT,
                    code = "breeding-result-already-recorded-for-year",
                    title = "Breeding result already recorded for the year",
                    detail = "この繁殖牝馬には指定された繁殖年の繁殖成績が既に記録されています。",
                )
                .apply {
                    setProperty("breeding_year", year.value)
                    setProperty("existing_breeding_result_id", existingBreedingResultId.value)
                }
    }

/**
 * [RecordUncoveredUseCaseError] を RFC 9457 (`application/problem+json`) の [ProblemDetail] に変換する。
 *
 * 種付記録（[RecordCoveringUseCaseError]）と対称に、繁殖登録の不在・前提条件違反（登録ロール）はいずれも整った 入力だが意味的に処理できないため 422
 * Unprocessable Entity、同一繁殖年の重複記録は状態の競合として 409 Conflict
 * とする。種付せずは配合相手を伴わないため、登録ロールの前提条件違反は繁殖牝馬でない（not-broodmare）の1種類のみ。
 */
fun RecordUncoveredUseCaseError.toProblemDetail(): ProblemDetail =
    when (this) {
        is RecordUncoveredUseCaseError.BreedingRegistrationNotFound ->
            problem(
                    status = HttpStatus.UNPROCESSABLE_ENTITY,
                    code = "breeding-registration-not-found",
                    title = "Breeding registration not found",
                    detail = "種付せずの記録対象として指定された繁殖登録が存在しません。",
                )
                .apply { setProperty("breeding_registration_id", breedingRegistrationId) }
        is RecordUncoveredUseCaseError.PreconditionViolated -> cause.toProblemDetail()
    }

private fun RecordUncoveredError.toProblemDetail(): ProblemDetail =
    when (this) {
        RecordUncoveredError.NotBroodmare ->
            problem(
                status = HttpStatus.UNPROCESSABLE_ENTITY,
                code = "not-broodmare",
                title = "Registration is not a broodmare",
                detail = "種付せずの記録対象として指定された繁殖登録のロールが繁殖牝馬ではありません。",
            )
        is RecordUncoveredError.AlreadyRecordedForYear ->
            problem(
                    status = HttpStatus.CONFLICT,
                    code = "breeding-result-already-recorded-for-year",
                    title = "Breeding result already recorded for the year",
                    detail = "この繁殖牝馬には指定された繁殖年の繁殖成績が既に記録されています。",
                )
                .apply {
                    setProperty("breeding_year", year.value)
                    setProperty("existing_breeding_result_id", existingBreedingResultId.value)
                }
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
