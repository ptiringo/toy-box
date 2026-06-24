package com.example.api.controller.horse.problem

import com.example.api.application.studbook.horse.NameHorseUseCaseError
import com.example.api.application.studbook.horse.RegisterImportedHorseUseCaseError
import com.example.api.application.studbook.horse.RegisterInStudBookUseCaseError
import com.example.api.controller.problem
import com.example.api.domain.studbook.model.horse.bloodhorse.RegisterInStudBookError
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail

/**
 * 軽種馬リソースの業務エラーを RFC 9457 (`application/problem+json`) の [ProblemDetail] へ変換するマッパー群。
 *
 * リソースに紐づく失敗バリアント（馬名登録・血統登録・輸入馬登録）ごとに、どのエラーをどの `status` / `errorCode` に描画するかの方針をここ（adapter 層の
 * `problem/` パッケージ）へ集約する。
 */

/**
 * [NameHorseUseCaseError] を RFC 9457 (`application/problem+json`) の [ProblemDetail] に変換する。
 *
 * - 馬名の不変条件違反は入力不正として 400 Bad Request
 * - 対象軽種馬の不在は、URL で指し示したリソースが存在しないため 404 Not Found
 * - 既に命名済みは、リソースの状態と要求が衝突するため 409 Conflict
 */
fun NameHorseUseCaseError.toProblemDetail(): ProblemDetail =
    when (this) {
        NameHorseUseCaseError.InvalidName ->
            problem(
                status = HttpStatus.BAD_REQUEST,
                code = "invalid-horse-name",
                title = "Invalid horse name",
                detail = "name はカタカナ 2〜9 文字でなければなりません。",
            )
        is NameHorseUseCaseError.HorseNotFound ->
            problem(
                    status = HttpStatus.NOT_FOUND,
                    code = "horse-not-found",
                    title = "Horse not found",
                    detail = "命名対象として指定された軽種馬が存在しません。",
                )
                .apply { setProperty("blood_horse_id", bloodHorseId) }
        is NameHorseUseCaseError.AlreadyNamed ->
            problem(
                    status = HttpStatus.CONFLICT,
                    code = "horse-already-named",
                    title = "Horse already named",
                    detail = "対象の軽種馬は既に命名済みのため、再命名はできません。",
                )
                .apply { setProperty("current_name", currentName) }
    }

/**
 * [RegisterInStudBookUseCaseError] を RFC 9457 (`application/problem+json`) の [ProblemDetail] に変換する。
 *
 * - VO 検証エラーは入力不正として 400 Bad Request
 * - 父母の不在（ボディ内 sire_id / dam_id の参照先不在）・ドメイン前提条件違反は、整った入力だが意味的に 処理できないため 422 Unprocessable
 *   Entity（判断基準は ADR-0018 / ADR-0021、api-design.md「404 vs 422」）
 */
fun RegisterInStudBookUseCaseError.toProblemDetail(): ProblemDetail =
    when (this) {
        RegisterInStudBookUseCaseError.InvalidRegistrationNumber ->
            problem(
                status = HttpStatus.BAD_REQUEST,
                code = "invalid-registration-number",
                title = "Invalid registration number",
                detail = "registration_number は空であってはいけません。",
            )
        RegisterInStudBookUseCaseError.InvalidMicrochipNumber ->
            problem(
                status = HttpStatus.BAD_REQUEST,
                code = "invalid-microchip-number",
                title = "Invalid microchip number",
                detail = "microchip_number は 15 桁の数字でなければなりません。",
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
                .apply { setProperty("sire_id", sireId) }
        is RegisterInStudBookUseCaseError.DamNotFound ->
            problem(
                    status = HttpStatus.UNPROCESSABLE_ENTITY,
                    code = "dam-not-found",
                    title = "Dam not found",
                    detail = "母として指定された軽種馬が存在しません。",
                )
                .apply { setProperty("dam_id", damId) }
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

/**
 * [RegisterImportedHorseUseCaseError] を RFC 9457 (`application/problem+json`) の [ProblemDetail] に
 * 変換する。
 *
 * 輸入馬登録は父母の引き当てを行わないため、失敗は VO 検証エラー（入力不正）のみで、すべて 400 Bad Request とする。
 */
fun RegisterImportedHorseUseCaseError.toProblemDetail(): ProblemDetail =
    when (this) {
        RegisterImportedHorseUseCaseError.InvalidRegistrationNumber ->
            problem(
                status = HttpStatus.BAD_REQUEST,
                code = "invalid-registration-number",
                title = "Invalid registration number",
                detail = "registration_number は空であってはいけません。",
            )
        RegisterImportedHorseUseCaseError.InvalidMicrochipNumber ->
            problem(
                status = HttpStatus.BAD_REQUEST,
                code = "invalid-microchip-number",
                title = "Invalid microchip number",
                detail = "microchip_number は 15 桁の数字でなければなりません。",
            )
        RegisterImportedHorseUseCaseError.BlankBreeder ->
            problem(
                status = HttpStatus.BAD_REQUEST,
                code = "blank-breeder",
                title = "Breeder is blank",
                detail = "breeder は空であってはいけません。",
            )
        RegisterImportedHorseUseCaseError.BlankOriginCountry ->
            problem(
                status = HttpStatus.BAD_REQUEST,
                code = "blank-origin-country",
                title = "Origin country is blank",
                detail = "origin_country は空であってはいけません。",
            )
    }
