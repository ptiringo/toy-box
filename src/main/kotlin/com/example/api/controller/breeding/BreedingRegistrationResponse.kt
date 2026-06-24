package com.example.api.controller.breeding

import com.example.api.application.studbook.breeding.RegisterBreedingRegistrationUseCaseError
import com.example.api.controller.problem
import com.example.api.domain.studbook.model.breeding.BreedingRegistration
import com.example.api.domain.studbook.model.breeding.BreedingRetirement
import java.time.LocalDate
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail

/**
 * 繁殖登録リソースの表現（HTTP 契約）。
 *
 * 繁殖登録に対する操作（登録の Create、今後加わる供用停止の `:retire` カスタムメソッド）は、 [AIP-133](https://google.aip.dev/133) /
 * [AIP-136](https://google.aip.dev/136) に倣い一律でこのリソース表現全体を 返す（[ADR-0008]
 * の単一リソース表現に整合）。供用中（[retirement] が null）と供用停止済み（事由と発生日を持つ）の 両状態を表せるよう、供用停止は任意の [retirement] として持つ。
 *
 * @property id 繁殖登録の生 UUID
 * @property registrationNumber 繁殖登録番号
 * @property registeredHorseId 繁殖登録した個体（血統登録済み）の生 UUID
 * @property role 繁殖登録によって付与されたロール（性から定まる）
 * @property retirement 供用停止。供用中なら null、供用停止済みなら事由と発生日を持つ
 */
data class BreedingRegistrationResponse(
    val id: UUID,
    val registrationNumber: String,
    val registeredHorseId: UUID,
    val role: BreedingRoleDto,
    val retirement: BreedingRetirementResponse?,
)

/**
 * 供用停止のリソース表現（HTTP 契約）。
 *
 * @property reason 供用停止の事由の区分
 * @property occurredOn 事由が発生した日
 */
data class BreedingRetirementResponse(val reason: RetirementReasonDto, val occurredOn: LocalDate)

/** [BreedingRegistration] を繁殖登録リソースの表現へ変換する。各操作の成功レスポンスはこのリソース表現を一律で返す。 */
fun BreedingRegistration.toResponse(): BreedingRegistrationResponse =
    BreedingRegistrationResponse(
        id = id.value,
        registrationNumber = registrationNumber.value,
        registeredHorseId = registeredHorseId.value,
        role = role.toDto(),
        retirement = retirement?.toResponse(),
    )

/** 供用停止 VO を HTTP 契約のリソース表現へ変換する。 */
fun BreedingRetirement.toResponse(): BreedingRetirementResponse =
    BreedingRetirementResponse(reason = reason.toDto(), occurredOn = occurredOn)

/**
 * [RegisterBreedingRegistrationUseCaseError] を RFC 9457 (`application/problem+json`) の
 * [ProblemDetail] に変換する。
 *
 * - 繁殖登録番号のブランクは入力不正として 400 Bad Request
 * - 対象個体の不在は、整った入力だが意味的に処理できないため 422 Unprocessable Entity（種付記録の繁殖登録不在と同じ扱い）
 */
fun RegisterBreedingRegistrationUseCaseError.toProblemDetail(): ProblemDetail =
    when (this) {
        RegisterBreedingRegistrationUseCaseError.BlankRegistrationNumber ->
            problem(
                status = HttpStatus.BAD_REQUEST,
                code = "invalid-breeding-registration-number",
                title = "Invalid breeding registration number",
                detail = "registration_number は空であってはいけません。",
            )
        is RegisterBreedingRegistrationUseCaseError.BloodHorseNotFound ->
            problem(
                    status = HttpStatus.UNPROCESSABLE_ENTITY,
                    code = "blood-horse-not-found",
                    title = "Blood horse not found",
                    detail = "繁殖登録の対象として指定された軽種馬が存在しません。",
                )
                .apply { setProperty("blood_horse_id", bloodHorseId) }
    }
