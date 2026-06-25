package com.example.api.controller.breeding.problem

import com.example.api.application.studbook.breeding.RegisterBreedingRegistrationUseCaseError
import com.example.api.controller.problem
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail

/**
 * 繁殖登録リソースの業務エラーを RFC 9457 (`application/problem+json`) の [ProblemDetail] へ変換するマッパー群。
 *
 * どのエラーをどの `status` / `errorCode` に描画するかの方針をここ（adapter 層の `problem/` パッケージ）へ集約する。
 */

/**
 * [RegisterBreedingRegistrationUseCaseError] を RFC 9457 の [ProblemDetail] に変換する。
 *
 * - 繁殖登録番号の VO 検証エラーは入力不正として 400 Bad Request
 * - リクエストボディで参照する軽種馬の不在は、整った入力だが意味的に処理できないため 422 Unprocessable Entity
 *   （api-design.md「リソース不在のステータス（404 vs 422）」: ボディ内参照先の不在は 422）
 */
fun RegisterBreedingRegistrationUseCaseError.toProblemDetail(): ProblemDetail =
    when (this) {
        RegisterBreedingRegistrationUseCaseError.InvalidRegistrationNumber ->
            problem(
                status = HttpStatus.BAD_REQUEST,
                code = "invalid-breeding-registration-number",
                title = "Invalid breeding registration number",
                detail = "registration_number は空であってはいけません。",
            )
        is RegisterBreedingRegistrationUseCaseError.HorseNotFound ->
            problem(
                    status = HttpStatus.UNPROCESSABLE_ENTITY,
                    code = "blood-horse-not-found",
                    title = "Blood horse not found",
                    detail = "繁殖登録の対象として指定された軽種馬が存在しません。",
                )
                .apply { setProperty("blood_horse_id", bloodHorseId) }
    }
