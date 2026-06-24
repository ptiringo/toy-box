package com.example.api.controller.breeding.problem

import com.example.api.application.studbook.breeding.RecordCoveringUseCaseError
import com.example.api.application.studbook.breeding.RecordUncoveredUseCaseError
import com.example.api.application.studbook.breeding.ReportFoalingUseCaseError
import com.example.api.controller.problem
import com.example.api.domain.studbook.model.breeding.CoveringValidityError
import com.example.api.domain.studbook.model.breeding.RecordCoveringError
import com.example.api.domain.studbook.model.breeding.RecordUncoveredError
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail

/**
 * 繁殖成績リソースの業務エラーを RFC 9457 (`application/problem+json`) の [ProblemDetail] へ変換するマッパー群。
 *
 * 種付記録・種付せず記録・分娩結果報告の失敗バリアントごとに、どのエラーをどの `status` / `errorCode` に 描画するかの方針をここ（adapter 層の `problem/`
 * パッケージ）へ集約する。
 */

/** 入力不正（VO 形式違反）を 400 Bad Request の [ProblemDetail] に描画する小さなビルダ。 */
private fun badRequest(code: String, title: String, detail: String): ProblemDetail =
    problem(status = HttpStatus.BAD_REQUEST, code = code, title = title, detail = detail)

/**
 * [RecordCoveringUseCaseError] を RFC 9457 (`application/problem+json`) の [ProblemDetail] に変換する。
 *
 * - VO 検証エラーは入力不正として 400 Bad Request
 * - 繁殖登録・種牡馬の不在やドメイン前提条件違反は、整った入力だが意味的に処理できないため 422 Unprocessable Entity
 */
fun RecordCoveringUseCaseError.toProblemDetail(): ProblemDetail =
    when (this) {
        RecordCoveringUseCaseError.InvalidCertificateNumber ->
            badRequest(
                "invalid-covering-certificate-number",
                "Invalid covering certificate number",
                "certificate_number は空であってはいけません。",
            )
        RecordCoveringUseCaseError.InvalidCoveringPlace ->
            badRequest(
                "invalid-covering-place",
                "Invalid covering place",
                "covering_place は空であってはいけません。",
            )
        RecordCoveringUseCaseError.InvalidStudCertificateNumber ->
            badRequest(
                "invalid-stud-certificate-number",
                "Invalid stud certificate number",
                "stud_certificate.number は空であってはいけません。",
            )
        RecordCoveringUseCaseError.InvalidValidRegion ->
            badRequest(
                "invalid-valid-region",
                "Invalid valid region",
                "stud_certificate.valid_regions の各区域名は空であってはいけません。",
            )
        RecordCoveringUseCaseError.InvalidValidityPeriod ->
            badRequest(
                "invalid-validity-period",
                "Invalid validity period",
                "stud_certificate の有効期間の終点は起点以降でなければなりません。",
            )
        RecordCoveringUseCaseError.EmptyValidRegions ->
            badRequest(
                "empty-valid-regions",
                "Empty valid regions",
                "stud_certificate.valid_regions は 1 つ以上指定してください。",
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
        is RecordCoveringError.InvalidCovering -> cause.toProblemDetail()
    }

/**
 * 種付の有効性検証の失敗（[CoveringValidityError]）を [ProblemDetail] に変換する。
 *
 * いずれも入力は整っているが種畜証明書の有効区域・有効期間を外れて意味的に処理できないため 422 Unprocessable Entity とする
 * （登録ロール違反と同じ扱い。api-design.md）。
 */
private fun CoveringValidityError.toProblemDetail(): ProblemDetail =
    when (this) {
        is CoveringValidityError.OutsideValidPeriod ->
            problem(
                    status = HttpStatus.UNPROCESSABLE_ENTITY,
                    code = "covering-outside-valid-period",
                    title = "Covering outside the stud certificate's valid period",
                    detail = "種付日が種畜証明書の有効期間外です。",
                )
                .apply {
                    setProperty("covering_date", coveringDate)
                    setProperty("valid_period_start", validPeriod.start)
                    setProperty("valid_period_end", validPeriod.end)
                }
        is CoveringValidityError.OutsideValidRegion ->
            problem(
                    status = HttpStatus.UNPROCESSABLE_ENTITY,
                    code = "covering-outside-valid-region",
                    title = "Covering outside the stud certificate's valid region",
                    detail = "種付場所が種畜証明書の有効区域外です。",
                )
                .apply {
                    setProperty("covering_place", coveringPlace.value)
                    setProperty("valid_regions", validRegions.map { it.value })
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
