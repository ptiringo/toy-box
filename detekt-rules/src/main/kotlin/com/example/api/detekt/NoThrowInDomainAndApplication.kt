package com.example.api.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import org.jetbrains.kotlin.psi.KtThrowExpression

/**
 * ドメイン / アプリケーション層で `throw` による例外送出を禁止する detekt カスタムルール。
 *
 * 業務エラーは `Result<V, E>`（kotlin-result）で型として返し、例外はインフラ障害・プログラミングエラーに限定する という Result-first
 * 方針（`.claude/rules/error-handling.md`）を機械的に強制する。例外化は Controller 境界の `orThrowProblem()`
 * 一手に閉じ込めるため、`controller` / `infrastructure` は対象外（=パッケージで除外）。
 *
 * 検出対象は明示的な `throw` 式（[KtThrowExpression]）のみ。`require` / `check` / `error` は関数呼び出しであり
 * 本ルールに掛からないため、プログラミングエラーの表明にはこれらを用いる。テストコードは detekt 設定の `excludes` で対象から外す。
 */
class NoThrowInDomainAndApplication(config: Config) :
    Rule(
        config,
        "ドメイン / アプリケーション層では throw せず Result-first を保つこと。" +
            "業務エラーは Result<V, E> で返し、プログラミングエラーは require / check を使う。",
    ) {
    override fun visitThrowExpression(expression: KtThrowExpression) {
        super.visitThrowExpression(expression)

        val packageName = expression.containingKtFile.packageFqName.asString()
        if (TARGET_PACKAGE_PREFIXES.any { packageName == it || packageName.startsWith("$it.") }) {
            report(
                Finding(
                    Entity.from(expression),
                    "ドメイン / アプリケーション層では throw しないこと。業務エラーは Result<V, E> で返し、" +
                        "プログラミングエラーは require / check を使う（.claude/rules/error-handling.md）。",
                )
            )
        }
    }

    private companion object {
        /** Result-first を強制する対象パッケージ（このプレフィックス配下を検査する）。 */
        val TARGET_PACKAGE_PREFIXES =
            listOf("com.example.api.domain", "com.example.api.application")
    }
}
