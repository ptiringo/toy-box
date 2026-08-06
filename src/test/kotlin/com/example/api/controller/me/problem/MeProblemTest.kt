package com.example.api.controller.me.problem

import com.example.api.application.iam.me.ProvisionMeError
import com.example.api.domain.iam.model.world.WorldNameValidationError
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

/**
 * [ProvisionMeError] の 3 分岐すべてが RFC 9457 の problem+json へ正しく写ることを検証する。
 *
 * `ProvisionMeError.InvalidDefaultWorldName` は既定の世界名（定数）の設定ミスでしか起きない防御的分岐で、 現実的な HTTP
 * 入力からは再現できない（[MeControllerTest] の KDoc を参照）。ここでは変換関数を直接呼んで、 `MeProblem.kt` の 3 分岐すべてに `src/test`
 * 由来のカバレッジを行き渡らせる。
 */
class MeProblemTest {

    @Test
    fun `InvalidSubject は 500 の problem に写る`() {
        val problem = ProvisionMeError.InvalidSubject.toProblemDetail()

        assert(problem.status == HttpStatus.INTERNAL_SERVER_ERROR.value())
        assert(problem.properties?.get("error_code") == "invalid-subject")
    }

    @Test
    fun `InvalidDefaultWorldName は 500 の problem に写る`() {
        val problem =
            ProvisionMeError.InvalidDefaultWorldName(WorldNameValidationError.TooLong)
                .toProblemDetail()

        assert(problem.status == HttpStatus.INTERNAL_SERVER_ERROR.value())
        assert(problem.properties?.get("error_code") == "invalid-default-world-name")
    }

    @Test
    fun `Conflict は 409 の problem に写る`() {
        val problem = ProvisionMeError.Conflict.toProblemDetail()

        assert(problem.status == HttpStatus.CONFLICT.value())
        assert(problem.properties?.get("error_code") == "provisioning-conflict")
    }
}
