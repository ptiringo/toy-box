package com.example.api.controller.horse

import com.example.api.application.studbook.horse.NameHorseUseCaseError
import com.example.api.controller.problem
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail

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
