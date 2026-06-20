package com.example.api.controller.racing

import com.example.api.application.horseracing.racing.RegisterAsRacehorseUseCaseError
import com.example.api.controller.problem
import com.example.api.domain.horseracing.model.racing.RacingRegistration
import com.example.api.domain.horseracing.service.racing.RegisterAsRacehorseError
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail

/**
 * 競走馬登録リソースの表現（HTTP 契約）。
 *
 * 競走馬登録の成功レスポンスはこのリソース表現を返す（AIP-133）。対象馬は登録済みの軽種馬IDで参照する。
 *
 * @property id 競走馬登録の生 UUID
 * @property registrationNumber 競走馬登録番号
 * @property racehorseId 競走馬（対象馬）の生 UUID
 */
data class RacingRegistrationResponse(
    val id: UUID,
    val registrationNumber: String,
    val racehorseId: UUID,
)

/** [RacingRegistration] を競走馬登録リソースの表現へ変換する。 */
fun RacingRegistration.toResponse(): RacingRegistrationResponse =
    RacingRegistrationResponse(
        id = id.value,
        registrationNumber = registrationNumber.value,
        racehorseId = racehorseId.value,
    )

/**
 * [RegisterAsRacehorseUseCaseError] を RFC 9457 形式の [ProblemDetail]（problem+json）に変換する。
 *
 * - VO 検証エラーは入力不正として 400 Bad Request
 * - 対象馬の不在・ドメイン前提条件違反は、整った入力だが意味的に処理できないため 422 Unprocessable Entity
 */
fun RegisterAsRacehorseUseCaseError.toProblemDetail(): ProblemDetail =
    when (this) {
        RegisterAsRacehorseUseCaseError.InvalidRegistrationNumber ->
            problem(
                status = HttpStatus.BAD_REQUEST,
                code = "invalid-registration-number",
                title = "Invalid registration number",
                detail = "registrationNumber は空であってはいけません。",
            )
        is RegisterAsRacehorseUseCaseError.HorseNotFound ->
            problem(
                    status = HttpStatus.UNPROCESSABLE_ENTITY,
                    code = "horse-not-found",
                    title = "Horse not found",
                    detail = "競走馬登録の対象として指定された軽種馬が存在しません。",
                )
                .apply { setProperty("bloodHorseId", bloodHorseId) }
        is RegisterAsRacehorseUseCaseError.PreconditionViolated -> cause.toProblemDetail()
    }

private fun RegisterAsRacehorseError.toProblemDetail(): ProblemDetail =
    when (this) {
        RegisterAsRacehorseError.NotNamed ->
            problem(
                status = HttpStatus.UNPROCESSABLE_ENTITY,
                code = "horse-not-named",
                title = "Horse is not named",
                detail = "競走馬登録には馬名登録が必要ですが、対象馬は未命名です。",
            )
    }
