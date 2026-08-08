package com.example.api.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * ユースケース（application 層の `invoke`）が操作主体（`Actor`）を第 1 引数に取ることを強制する detekt カスタムルール。
 *
 * `Actor` は「どのアカウントの、どの世界に対する操作か」を運ぶ値で、認証・所有権チェックを通った結果として adapter から渡される（#705 /
 * ADR-0067）。ユースケースがこれを受け取らないと、世界を自分で決める（＝他人の 世界に触れる、あるいは全世界を横断する）余地が残る。
 *
 * 読み取り・書き込みを問わず適用する。読み取りのスコープ漏れは DB が守らない（`WHERE world_id = ?` を 書き忘れた SELECT は複合 FK
 * を素通りする）ため、読みを外す理由がない（#706）。
 *
 * `application.<context>..` の `invoke` を対象とし、`<context> == iam` は対象外。世界を作る操作にはまだ世界が 無く、スコープは
 * `AccountId` で表す（`CreateWorldUseCase.invoke(accountId, command)`）。
 *
 * 既知の限界:
 * - ソースのテキストで判定するため `typealias` による迂回は検出できない（レビュー担保）。
 * - 対象集合は関数名が `invoke` であることのみに依存する（[WorldScopedPortSignature] の `〜Repository` / `〜Queries`
 *   選定と同様）。この命名規約自体を機械強制する仕組みは無いため、規約から外れた宣言は静かに 対象外になる。規約の遵守自体はレビューで担保する。
 * - 関数名 `invoke` だけで対象を絞り、宣言の形（companion object の `invoke` ファクトリ・ローカル関数）では 絞っていない。現状 `application`
 *   配下に false positive は 0 件だが、将来 `application` 配下へ `companion object { operator fun invoke(...) }`
 *   を書くとこのルールに掛かる。失敗は loud なので気づける ため、宣言形での絞り込みは実装しない（オーバーエンジニアリング）。
 */
class ActorScopedUseCase(config: Config) :
    Rule(
        config,
        "application 層の invoke は第 1 引数に Actor を取ること。" +
            "認証・所有権チェックを通った操作主体を受け取らないと、世界を自分で決める余地が残る（ADR-0067）。",
    ) {
    override fun visitNamedFunction(function: KtNamedFunction) {
        super.visitNamedFunction(function)

        if (function.name != "invoke") return
        val (layer, context) =
            layerAndContext(function.containingKtFile.packageFqName.asString()) ?: return
        if (layer != "application" || context == IAM_CONTEXT) return
        if (function.valueParameters.firstOrNull()?.typeReference.denotesType("Actor")) return

        val message =
            "ユースケースの invoke が第 1 引数に Actor を取っていない。" + "操作主体（アカウントと世界）を受け取ること（ADR-0067 / #706）。"
        report(Finding(Entity.from(function), message))
    }
}
