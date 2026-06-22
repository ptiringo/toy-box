package com.example.api.controller.breeding

import com.example.api.application.horseracing.breeding.RecordCoveringCommand
import com.example.api.application.horseracing.breeding.RecordUncoveredCommand
import com.example.api.controller.problem
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import java.time.LocalDate
import java.time.Year
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail

/**
 * `POST /api/breedingResults` のリクエストボディ。繁殖成績の年次レコードを起こす Create。
 *
 * 様式第14号の年次成績は「種付した年」と「種付しなかった年（種付せず）」の2種類があり、ドメインの `covering` が nullable であることに合わせ、本リクエストは
 * [covering] の有無で両者を判別する単一の Create とする（[ADR-0008] の 単一リソース表現に整合）。
 * - [covering] が非 null: その年の種付を記録する。繁殖年は種付日から導出されるため [breedingYear] は不要（無視する）。
 * - [covering] が null: 種付せず（その年に種付しなかった）の年次成績を記録する。繁殖年は種付日から導出できないため [breedingYear]
 *   が必須（欠ける場合は入力不正として 400）。
 *
 * @property breedingRegistrationId 記録対象の繁殖牝馬の繁殖登録ID
 * @property breedingYear 繁殖年。種付せず（[covering] が null）のとき必須。種付ありなら無視される
 * @property covering その年の種付。種付せずの年は null
 */
data class RecordBreedingResultRequest(
    val breedingRegistrationId: UUID,
    val breedingYear: Int?,
    val covering: CoveringRequest?,
)

/**
 * 種付の入力（[RecordBreedingResultRequest.covering]）。
 *
 * 繁殖牝馬・種牡馬はいずれも登録済みの繁殖登録IDで参照する。VO で表す種付証明書番号は素の文字列で受け取り、 ユースケースで検証する。
 *
 * @property stallionRegistrationId 配合相手の種牡馬の繁殖登録ID
 * @property coveringDate 種付日
 * @property certificateNumber 種付証明書番号
 */
data class CoveringRequest(
    val stallionRegistrationId: UUID,
    val coveringDate: LocalDate,
    val certificateNumber: String,
)

/** 種付ありの入力を種付記録ユースケースの入力コマンドへ変換する。 */
fun RecordBreedingResultRequest.toCoveringCommand(
    covering: CoveringRequest
): RecordCoveringCommand =
    RecordCoveringCommand(
        breedingRegistrationId = breedingRegistrationId,
        stallionRegistrationId = covering.stallionRegistrationId,
        coveringDate = covering.coveringDate,
        certificateNumber = covering.certificateNumber,
    )

/**
 * 種付せず（[covering] が null）の入力を種付せず記録ユースケースの入力コマンドへ変換する。
 *
 * 種付せずは繁殖年を種付日から導出できないため [breedingYear] が必須。欠けている場合は入力不正として 400 を表す [ProblemDetail] を返す。
 */
fun RecordBreedingResultRequest.toUncoveredCommand():
    Result<RecordUncoveredCommand, ProblemDetail> =
    if (breedingYear == null) {
        Err(
            problem(
                status = HttpStatus.BAD_REQUEST,
                code = "missing-breeding-year",
                title = "Missing breeding year",
                detail = "種付せず（covering 無し）のときは breeding_year が必須です。",
            )
        )
    } else {
        Ok(
            RecordUncoveredCommand(
                breedingRegistrationId = breedingRegistrationId,
                breedingYear = Year.of(breedingYear),
            )
        )
    }
