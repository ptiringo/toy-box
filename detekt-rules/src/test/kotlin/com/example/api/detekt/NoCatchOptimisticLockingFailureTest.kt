package com.example.api.detekt

import dev.detekt.test.TestConfig
import dev.detekt.test.lint
import org.junit.jupiter.api.Test

/**
 * [NoCatchOptimisticLockingFailure] のルール挙動を検証する。
 *
 * ルールが「infrastructure での楽観ロック競合の catch を検出する」「lockRowIfVersionMatches を使う適合コードや 他の例外の catch
 * は検出しない」ことを能動的に確かめる（#890 の完了条件。`.claude/rules/gates.md`）。
 */
class NoCatchOptimisticLockingFailureTest {
    private val rule = NoCatchOptimisticLockingFailure(TestConfig())

    @Test
    fun `infrastructure での楽観ロック競合の catch を検出すること`() {
        val findings =
            rule.lint(
                """
                package com.example.api.infrastructure.studbook.horse

                fun save(): String =
                    try {
                        "ok"
                    } catch (_: OptimisticLockingFailureException) {
                        "conflict"
                    }
                """
                    .trimIndent()
            )

        assert(findings.size == 1)
    }

    @Test
    fun `完全修飾で書かれた catch も検出すること`() {
        // 型解決なしの PSI で動かしているため、ソース上の表記ゆれをルール側で吸収する必要がある。
        val findings =
            rule.lint(
                """
                package com.example.api.infrastructure.studbook.horse

                fun save(): String =
                    try {
                        "ok"
                    } catch (e: org.springframework.dao.OptimisticLockingFailureException) {
                        e.message ?: "conflict"
                    }
                """
                    .trimIndent()
            )

        assert(findings.size == 1)
    }

    @Test
    fun `サブタイプの catch も検出すること`() {
        // ObjectOptimisticLockingFailureException で受けても global rollback-only は同じく立つ。
        val findings =
            rule.lint(
                """
                package com.example.api.infrastructure.iam.world

                fun save(): String =
                    try {
                        "ok"
                    } catch (_: ObjectOptimisticLockingFailureException) {
                        "conflict"
                    }
                """
                    .trimIndent()
            )

        assert(findings.size == 1)
    }

    @Test
    fun `lockRowIfVersionMatches で版を突き合わせる適合コードは検出しないこと`() {
        // 正しい形は例外を起こさせないこと（#867 / PR #888）。
        val findings =
            rule.lint(
                """
                package com.example.api.infrastructure.studbook.horse

                fun save(): String {
                    if (!lockRowIfVersionMatches()) return "conflict"
                    return "ok"
                }
                """
                    .trimIndent()
            )

        assert(findings.isEmpty())
    }

    @Test
    fun `他のデータアクセス例外の catch は検出しないこと`() {
        // UNIQUE 違反等を捕まえる経路まで巻き込まない。
        val findings =
            rule.lint(
                """
                package com.example.api.infrastructure.studbook.horse

                fun save(): String =
                    try {
                        "ok"
                    } catch (_: DuplicateKeyException) {
                        "duplicated"
                    }
                """
                    .trimIndent()
            )

        assert(findings.isEmpty())
    }

    @Test
    fun `infrastructure 以外の層は検出しないこと`() {
        // この誤りが成立するのは永続化アダプタだけなので、検出範囲を infrastructure に限る。
        val findings =
            rule.lint(
                """
                package com.example.api.controller.studbook

                fun handle(): String =
                    try {
                        "ok"
                    } catch (_: OptimisticLockingFailureException) {
                        "conflict"
                    }
                """
                    .trimIndent()
            )

        assert(findings.isEmpty())
    }
}
