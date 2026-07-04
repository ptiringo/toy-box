package com.example.api.detekt

import dev.detekt.test.TestConfig
import dev.detekt.test.lint
import org.junit.jupiter.api.Test

/**
 * [NoSilentMapError] のルール挙動を検証する。
 *
 * ルールが「変換元エラーを参照も型明示もしない `mapError` ラムダを検出する」「参照・型明示のある変換や `mapError`
 * 以外の呼び出しは検出しない」ことを能動的に確かめる（#541 の完了条件）。
 */
class NoSilentMapErrorTest {
    private val rule = NoSilentMapError(TestConfig())

    @Test
    fun `暗黙 it を参照しない mapError ラムダを検出すること`() {
        val findings =
            rule.lint(
                """
                package com.example.api.application.studbook.breeding

                fun convert(result: Result<Value, BlankValue>): Result<Value, UseCaseError> =
                    result.mapError { UseCaseError.InvalidValue }
                """
                    .trimIndent()
            )

        assert(findings.size == 1)
    }

    @Test
    fun `暗黙 it を参照する mapError ラムダは検出しないこと`() {
        // cause を保持して wrap する本線パターン（PreconditionViolated(it) 等）は対象外。
        val findings =
            rule.lint(
                """
                package com.example.api.application.studbook.breeding

                fun convert(result: Result<Value, RecordError>): Result<Value, UseCaseError> =
                    result.mapError { UseCaseError.PreconditionViolated(it) }
                """
                    .trimIndent()
            )

        assert(findings.isEmpty())
    }

    @Test
    fun `ネストしたラムダの暗黙 it は外側の参照と数えず検出すること`() {
        // 内側のパラメータ宣言なしラムダは外側の it をシャドーイングする。内側の it を
        // 外側の参照と誤認すると偽陰性になるため、能動的に検出を確かめる。
        val findings =
            rule.lint(
                """
                package com.example.api.application.studbook.breeding

                fun convert(result: Result<Value, BlankValue>): Result<Value, UseCaseError> =
                    result.mapError { UseCaseError.InvalidValues(names.map { it.value }) }
                """
                    .trimIndent()
            )

        assert(findings.size == 1)
    }

    @Test
    fun `型注釈のない _ パラメータ宣言の mapError ラムダを検出すること`() {
        val findings =
            rule.lint(
                """
                package com.example.api.application.studbook.breeding

                fun convert(result: Result<Value, BlankValue>): Result<Value, UseCaseError> =
                    result.mapError { _ -> UseCaseError.InvalidValue }
                """
                    .trimIndent()
            )

        assert(findings.size == 1)
    }

    @Test
    fun `参照されない型注釈なしの名前付きパラメータ宣言の mapError ラムダを検出すること`() {
        val findings =
            rule.lint(
                """
                package com.example.api.application.studbook.breeding

                fun convert(result: Result<Value, BlankValue>): Result<Value, UseCaseError> =
                    result.mapError { cause -> UseCaseError.InvalidValue }
                """
                    .trimIndent()
            )

        assert(findings.size == 1)
    }

    @Test
    fun `型注釈のある _ パラメータ宣言の mapError ラムダは検出しないこと`() {
        // 変換元型の明示がトリップワイヤになる（エラー型の sealed 昇格時にコンパイルエラーで検知）。
        val findings =
            rule.lint(
                """
                package com.example.api.application.studbook.breeding

                fun convert(result: Result<Value, BlankValue>): Result<Value, UseCaseError> =
                    result.mapError { _: BlankValue -> UseCaseError.InvalidValue }
                """
                    .trimIndent()
            )

        assert(findings.isEmpty())
    }

    @Test
    fun `参照される名前付きパラメータ宣言の mapError ラムダは検出しないこと`() {
        val findings =
            rule.lint(
                """
                package com.example.api.application.studbook.breeding

                fun convert(result: Result<Value, RecordError>): Result<Value, UseCaseError> =
                    result.mapError { cause -> UseCaseError.PreconditionViolated(cause) }
                """
                    .trimIndent()
            )

        assert(findings.isEmpty())
    }

    @Test
    fun `パラメータ宣言のあるネストラムダ内の it は外側の参照として数えること`() {
        // パラメータ宣言のあるネストラムダは暗黙 it を導入しないため、その中の it は外側の it。
        val findings =
            rule.lint(
                """
                package com.example.api.application.studbook.breeding

                fun convert(result: Result<Value, RecordError>): Result<Value, UseCaseError> =
                    result.mapError { names.map { n -> UseCaseError.Violated(n, it) }.first() }
                """
                    .trimIndent()
            )

        assert(findings.isEmpty())
    }

    @Test
    fun `mapError 以外の呼び出しはパラメータを無視しても検出しないこと`() {
        val findings =
            rule.lint(
                """
                package com.example.api.application.studbook.breeding

                fun fallback(result: Result<Value, BlankValue>): Value =
                    result.getOrElse { Value.DEFAULT }
                """
                    .trimIndent()
            )

        assert(findings.isEmpty())
    }

    @Test
    fun `ラムダでなく関数参照を渡す mapError は検出しないこと`() {
        // 関数参照は型付きシグネチャを持つため、エラー型の付け替え時にコンパイルエラーになる。
        val findings =
            rule.lint(
                """
                package com.example.api.application.studbook.breeding

                fun convert(result: Result<Value, BlankValue>): Result<Value, UseCaseError> =
                    result.mapError(::toUseCaseError)
                """
                    .trimIndent()
            )

        assert(findings.isEmpty())
    }
}
