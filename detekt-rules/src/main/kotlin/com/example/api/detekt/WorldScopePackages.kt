package com.example.api.detekt

import org.jetbrains.kotlin.psi.KtTypeReference

/**
 * 世界スコープ（テナント分離）の引数契約を検査するルール群（[WorldScopedPortSignature] /
 * [ActorScopedUseCase]）が共有する、パッケージ・型判定のヘルパ。
 *
 * `iam` の除外定義をこの 1 か所に集約する（`Account` / `World` はテナントの根であり世界に属さないため、 どのルールでも対象外にする。ADR-0067 / #706）。
 */

/** プロダクションコードのルートパッケージ。 */
private const val BASE_PACKAGE = "com.example.api"

/** テナントの根を持つコンテキスト。世界スコープの引数契約は課さない。 */
internal const val IAM_CONTEXT = "iam"

/**
 * パッケージ名から層（`domain` / `application` / `controller` / `infrastructure`）と、その直下の 境界づけられたコンテキスト名を取り出す。
 *
 * 例: `com.example.api.domain.studbook.model.horse` -> `"domain" to "studbook"`。
 * ルートパッケージ配下でない、または層の下にコンテキストが無い（`com.example.api.domain` 等）場合は null を返す。
 */
internal fun layerAndContext(packageName: String): Pair<String, String>? {
    val prefix = "$BASE_PACKAGE."
    if (!packageName.startsWith(prefix)) return null
    val segments = packageName.removePrefix(prefix).split('.')
    if (segments.size < 2) return null
    return segments[0] to segments[1]
}

/**
 * パッケージ名の層直下から数えて [index] 番目のセグメントを返す（層直下のコンテキストが 0 番目）。
 *
 * リポジトリポートを `domain.<context>.model..` に限定する判定に用いる。
 */
internal fun packageSegmentAfterLayer(packageName: String, index: Int): String? {
    val prefix = "$BASE_PACKAGE."
    if (!packageName.startsWith(prefix)) return null
    return packageName.removePrefix(prefix).split('.').getOrNull(index + 1)
}

/**
 * 型参照が単純名 [simpleName] の型を指しているかを返す。
 *
 * 完全修飾（`com.example.api.domain.shared.WorldId`）で書かれても通るよう末尾セグメントで比較する。 detekt は型解決なしの PSI
 * で動かしているため、ここで見えるのはソース上のテキストだけである。
 */
internal fun KtTypeReference?.denotesType(simpleName: String): Boolean =
    this?.text?.substringAfterLast('.') == simpleName
