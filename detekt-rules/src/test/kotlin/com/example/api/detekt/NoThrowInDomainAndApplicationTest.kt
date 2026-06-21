package com.example.api.detekt

import dev.detekt.test.TestConfig
import dev.detekt.test.lint
import org.junit.jupiter.api.Test

/**
 * [NoThrowInDomainAndApplication] のルール挙動を検証する。
 *
 * ルールが「ドメイン / アプリケーション層の throw を検出する」「他層（controller / infrastructure）や require / check
 * は検出しない」ことを能動的に確かめる（#298 の完了条件）。
 */
class NoThrowInDomainAndApplicationTest {
    private val rule = NoThrowInDomainAndApplication(TestConfig())

    @Test
    fun `ドメイン層の throw を検出すること`() {
        val findings =
            rule.lint(
                """
                package com.example.api.domain.horseracing.model.horse

                fun assignName(name: String) {
                    if (name.isBlank()) throw IllegalArgumentException("blank")
                }
                """
                    .trimIndent()
            )

        assert(findings.size == 1)
    }

    @Test
    fun `アプリケーション層の throw を検出すること`() {
        val findings =
            rule.lint(
                """
                package com.example.api.application.horseracing

                fun register() {
                    throw RuntimeException("boom")
                }
                """
                    .trimIndent()
            )

        assert(findings.size == 1)
    }

    @Test
    fun `controller 層の throw は検出しないこと`() {
        // Controller 境界（orThrowProblem）での例外化は許容される（描画 funnel への委譲）。
        val findings =
            rule.lint(
                """
                package com.example.api.controller.horse

                fun handle() {
                    throw IllegalStateException("rendered as problem")
                }
                """
                    .trimIndent()
            )

        assert(findings.isEmpty())
    }

    @Test
    fun `infrastructure 層の throw は検出しないこと`() {
        // インフラ障害は例外で表現してよい。
        val findings =
            rule.lint(
                """
                package com.example.api.infrastructure.horseracing

                fun query() {
                    throw RuntimeException("db down")
                }
                """
                    .trimIndent()
            )

        assert(findings.isEmpty())
    }

    @Test
    fun `require や check は throw 式ではないため検出しないこと`() {
        // プログラミングエラーの表明は require / check に寄せる（関数呼び出しなので本ルール対象外）。
        val findings =
            rule.lint(
                """
                package com.example.api.domain.horseracing.model.horse

                fun assignName(name: String) {
                    require(name.isNotBlank()) { "blank" }
                    check(name.length < 100) { "too long" }
                }
                """
                    .trimIndent()
            )

        assert(findings.isEmpty())
    }
}
