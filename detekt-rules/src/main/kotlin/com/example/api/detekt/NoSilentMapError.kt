package com.example.api.detekt

import com.intellij.psi.PsiElement
import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

/**
 * `mapError` のラムダが変換元エラーを参照も型明示もせずに固定値へ写す「握りつぶし」を検出する detekt カスタムルール。
 *
 * 変換元が単一エラー型の `mapError` は、ラムダパラメータを無視するとエラー型が sealed interface へ昇格（バリアント追加）
 * されてもコンパイルが通り、新バリアントが既存の誤った errorCode・メッセージへ無音で変換される（`.claude/rules/error-handling.md`）。
 * ラムダパラメータの参照または型明示（`_: 変換元エラー型 ->`）を強制し、型の付け替え時に全変換箇所を コンパイルエラーで浮かび上がらせる。
 */
class NoSilentMapError(config: Config) :
    Rule(
        config,
        "mapError のラムダは変換元エラーを参照するか、パラメータの型を明示すること（_: 変換元エラー型 ->）。" +
            "型を明示しておくと、エラー型の sealed 昇格時に変換箇所がコンパイルエラーで検知できる。",
    ) {
    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)

        if (expression.calleeExpression?.text != "mapError") return
        val lambda = expression.lambdaArguments.singleOrNull()?.getLambdaExpression() ?: return
        val body = lambda.bodyExpression ?: return

        val silent =
            when (val param = lambda.valueParameters.singleOrNull()) {
                // パラメータ宣言なし: 暗黙 it を参照していなければ握りつぶし
                null -> lambda.valueParameters.isEmpty() && !referencesOuterIt(body)
                // 宣言あり: 型明示があればトリップワイヤとして OK。なければ参照の有無で判定
                else ->
                    param.typeReference == null &&
                        (param.name == "_" || !referencesName(body, param.name))
            }
        if (silent) {
            report(
                Finding(
                    Entity.from(expression),
                    "mapError のラムダが変換元エラーを参照も型明示もしていない。エラーを参照するか、" +
                        "パラメータの型を明示すること（_: 変換元エラー型 ->。.claude/rules/error-handling.md）。",
                )
            )
        }
    }

    /** [element] 配下に名前 [name] への参照があるかを返す。 */
    private fun referencesName(element: PsiElement, name: String?): Boolean {
        if (element is KtNameReferenceExpression && element.getReferencedName() == name) return true
        return element.children.any { referencesName(it, name) }
    }

    /**
     * [element] 配下に外側ラムダの暗黙 `it` への参照があるかを返す。
     *
     * パラメータ宣言のないネストラムダは自身の暗黙 `it` を導入して外側をシャドーイングするため、 その配下は走査しない（内側の `it`
     * を外側の参照と誤認する偽陰性を防ぐ）。パラメータ宣言のある ネストラムダは暗黙 `it` を導入しないため、配下の `it` は外側の参照として数える。
     */
    private fun referencesOuterIt(element: PsiElement): Boolean {
        if (element is KtNameReferenceExpression && element.getReferencedName() == "it") return true
        return element.children.any { child ->
            val shadowsIt = child is KtLambdaExpression && child.valueParameters.isEmpty()
            !shadowsIt && referencesOuterIt(child)
        }
    }
}
