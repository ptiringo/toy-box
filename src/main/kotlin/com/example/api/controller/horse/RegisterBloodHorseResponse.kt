package com.example.api.controller.horse

import com.example.api.application.horseracing.horse.RegisterInStudBookUseCaseError
import com.example.api.controller.problem
import com.example.api.domain.horseracing.model.horse.bloodhorse.BloodHorse
import com.example.api.domain.horseracing.service.horse.RegisterInStudBookError
import java.time.LocalDate
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail

/**
 * `POST /api/blood_horses` の成功レスポンスボディ。
 *
 * 血統登録で誕生した軽種馬の全体を表す。父・母は登録済みの軽種馬IDで参照する。
 *
 * @property id 登録された軽種馬の生 UUID
 * @property registrationNumber 血統登録番号
 * @property sex 性
 * @property coatColor 毛色
 * @property breedType 品種
 * @property dateOfBirth 生年月日
 * @property breeder 生産者名
 * @property microchipNumber マイクロチップ番号
 * @property sireId 父（雄）の生 UUID
 * @property damId 母（雌）の生 UUID
 */
@Suppress("LongParameterList") // 登録された resource 全体を返すため項目数が多いのは必然
data class RegisterBloodHorseResponse(
    val id: UUID,
    val registrationNumber: String,
    val sex: SexDto,
    val coatColor: CoatColorDto,
    val breedType: BreedTypeDto,
    val dateOfBirth: LocalDate,
    val breeder: String,
    val microchipNumber: String,
    val sireId: UUID,
    val damId: UUID,
)

/** [BloodHorse] を `POST /api/blood_horses` の成功レスポンスへ変換する。 */
fun BloodHorse.toRegisterResponse(): RegisterBloodHorseResponse =
    RegisterBloodHorseResponse(
        id = id.value,
        registrationNumber = registrationNumber.value,
        sex = sex.toApi(),
        coatColor = coatColor.toApi(),
        breedType = breedType.toApi(),
        dateOfBirth = dateOfBirth.value,
        breeder = breeder.name,
        microchipNumber = microchipNumber.value,
        sireId = sireId.value,
        damId = damId.value,
    )

/**
 * [RegisterInStudBookUseCaseError] を RFC 9457 (`application/problem+json`) の [ProblemDetail] に変換する。
 *
 * - VO 検証エラーは入力不正として 400 Bad Request
 * - 父母の不在・ドメイン前提条件違反は、整った入力だが意味的に処理できないため 422 Unprocessable Entity
 */
fun RegisterInStudBookUseCaseError.toProblemDetail(): ProblemDetail =
    when (this) {
        RegisterInStudBookUseCaseError.InvalidRegistrationNumber ->
            problem(
                status = HttpStatus.BAD_REQUEST,
                code = "invalid-registration-number",
                title = "Invalid registration number",
                detail = "registrationNumber は空であってはいけません。",
            )
        RegisterInStudBookUseCaseError.InvalidMicrochipNumber ->
            problem(
                status = HttpStatus.BAD_REQUEST,
                code = "invalid-microchip-number",
                title = "Invalid microchip number",
                detail = "microchipNumber は 15 桁の数字でなければなりません。",
            )
        RegisterInStudBookUseCaseError.BlankBreeder ->
            problem(
                status = HttpStatus.BAD_REQUEST,
                code = "blank-breeder",
                title = "Breeder is blank",
                detail = "breeder は空であってはいけません。",
            )
        is RegisterInStudBookUseCaseError.SireNotFound ->
            problem(
                    status = HttpStatus.UNPROCESSABLE_ENTITY,
                    code = "sire-not-found",
                    title = "Sire not found",
                    detail = "父として指定された軽種馬が存在しません。",
                )
                .apply { setProperty("sireId", sireId) }
        is RegisterInStudBookUseCaseError.DamNotFound ->
            problem(
                    status = HttpStatus.UNPROCESSABLE_ENTITY,
                    code = "dam-not-found",
                    title = "Dam not found",
                    detail = "母として指定された軽種馬が存在しません。",
                )
                .apply { setProperty("damId", damId) }
        is RegisterInStudBookUseCaseError.PreconditionViolated -> cause.toProblemDetail()
    }

private fun RegisterInStudBookError.toProblemDetail(): ProblemDetail =
    when (this) {
        RegisterInStudBookError.SireNotMale ->
            problem(
                status = HttpStatus.UNPROCESSABLE_ENTITY,
                code = "sire-not-male",
                title = "Sire is not male",
                detail = "父として指定された軽種馬が雄ではありません。",
            )
        RegisterInStudBookError.DamNotFemale ->
            problem(
                status = HttpStatus.UNPROCESSABLE_ENTITY,
                code = "dam-not-female",
                title = "Dam is not female",
                detail = "母として指定された軽種馬が雌ではありません。",
            )
        RegisterInStudBookError.ParentageNotConfirmed ->
            problem(
                status = HttpStatus.UNPROCESSABLE_ENTITY,
                code = "parentage-not-confirmed",
                title = "Parentage not confirmed",
                detail = "申告された父母との DNA 型による親子判定が確認できません。",
            )
        RegisterInStudBookError.BreedMismatch ->
            problem(
                status = HttpStatus.UNPROCESSABLE_ENTITY,
                code = "breed-mismatch",
                title = "Breed mismatch",
                detail = "仔の品種が父母の品種と整合しません。",
            )
    }
