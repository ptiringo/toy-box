package com.example.api.controller.world.problem

import com.example.api.application.iam.world.CreateWorldError
import com.example.api.application.iam.world.WorldMutationError
import com.example.api.controller.problem
import com.example.api.domain.iam.model.world.WorldNameValidationError
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail

/** 世界作成の失敗を RFC 9457 の problem+json へ写す。 */
fun CreateWorldError.toProblemDetail(): ProblemDetail =
    when (this) {
        is CreateWorldError.InvalidName -> cause.toProblemDetail()
        is CreateWorldError.Conflict ->
            problem(
                status = HttpStatus.CONFLICT,
                code = "world-name-taken",
                title = "World name already taken",
                detail = "同じ名前の世界を 2 つ持つことはできません。別の名前を指定してください。",
            )
    }

/** 既存の世界に対する操作の失敗を RFC 9457 の problem+json へ写す。 */
fun WorldMutationError.toProblemDetail(): ProblemDetail =
    when (this) {
        // 他人の世界を指した場合もここに来る。存在を漏らさないため 403 ではなく 404 に潰す。
        is WorldMutationError.NotFound ->
            problem(
                    status = HttpStatus.NOT_FOUND,
                    code = "world-not-found",
                    title = "World not found",
                    detail = "指定された世界は存在しません。",
                )
                .apply { setProperty("world_id", worldId.value) }
        is WorldMutationError.InvalidName -> cause.toProblemDetail()
        is WorldMutationError.Conflict ->
            problem(
                status = HttpStatus.CONFLICT,
                code = "world-update-conflict",
                title = "World update conflict",
                detail = "同名の世界が既にあるか、別の更新と競合しました。やり直してください。",
            )
    }

private fun WorldNameValidationError.toProblemDetail(): ProblemDetail =
    when (this) {
        WorldNameValidationError.Blank ->
            problem(
                status = HttpStatus.BAD_REQUEST,
                code = "world-name-blank",
                title = "World name is blank",
                detail = "name は空であってはいけません。",
            )
        WorldNameValidationError.TooLong ->
            problem(
                status = HttpStatus.BAD_REQUEST,
                code = "world-name-too-long",
                title = "World name is too long",
                detail = "name は 64 文字以下で指定してください。",
            )
    }
