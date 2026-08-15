package com.example.api.controller.breeding.problem

import com.example.api.application.studbook.breeding.BreedingRegistrationNotFound
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
 * - 申請された繁殖登録番号が既に他の繁殖登録に採番済みは、原簿の既存登録番号と衝突するため 409 Conflict。 血統登録番号とは別の採番空間のため、血統側
 *   （`registration-number-already-taken`）とは別の `errorCode` を割り当てる
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
        is RegisterBreedingRegistrationUseCaseError.RegistrationNumberAlreadyTaken ->
            problem(
                    status = HttpStatus.CONFLICT,
                    code = "breeding-registration-number-already-taken",
                    title = "Breeding registration number already taken",
                    detail = "申請された繁殖登録番号は既に他の繁殖登録に採番されています。",
                )
                .apply { setProperty("registration_number", registrationNumber) }
        is RegisterBreedingRegistrationUseCaseError.HorseNotFound ->
            problem(
                    status = HttpStatus.UNPROCESSABLE_CONTENT,
                    code = "blood-horse-not-found",
                    title = "Blood horse not found",
                    detail = "繁殖登録の対象として指定された軽種馬が存在しません。",
                )
                .apply { setProperty("blood_horse_id", bloodHorseId) }
    }

/**
 * [BreedingRegistrationNotFound]（照会対象の繁殖登録不在）を 404 Not Found の [ProblemDetail] に変換する。
 *
 * URL パス上の操作対象が不在のケースなので 404（api-design.md「リソース不在のステータス（404 vs 422）」）。
 *
 * `breeding-registration-not-found` は種付記録・種付せず記録のボディ内参照先不在（422）で既に使われている。 同一 type が 404 と 422
 * の両方を持つと場合分けを濁すため流用せず、パス上の対象を指す `registration-not-found` を割り当てる（軽種馬が 422 = `blood-horse-not-found`
 * / 404 = `horse-not-found` と非対称なのと同じ考え方で、 ボディ参照は限定語・パス対象は一般語を使う）。
 */
fun BreedingRegistrationNotFound.toProblemDetail(): ProblemDetail =
    problem(
            status = HttpStatus.NOT_FOUND,
            code = "registration-not-found",
            title = "Breeding registration not found",
            detail = "指定された ID の繁殖登録は存在しません。",
        )
        .apply { setProperty("breeding_registration_id", id) }
