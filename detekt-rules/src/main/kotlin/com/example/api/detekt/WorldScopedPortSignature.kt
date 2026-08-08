package com.example.api.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject

/**
 * 永続化ポート（Repository / Queries）が世界スコープを引数で受け取ることを強制する detekt カスタムルール。
 *
 * データは世界（セーブデータ＝テナント）ごとに閉じており、集約自身は世界を知らないためスコープは引数で運ぶ （#704 / ADR-0067）。第 1 引数に `WorldId`
 * を要求することで、世界を受け取らないポートを構造的に作れなくする。 とくに読み取りは DB が守らない（`WHERE world_id = ?` を書き忘れた SELECT は複合 FK
 * を素通りする）ため、 このルールが最後の砦になる（#706）。
 *
 * **ArchUnit ではなく detekt に置く理由**: `WorldId` は `@JvmInline value class` であり、バイトコードでは 引数が下地型
 * `java.util.UUID` へ unbox される（`findByFullName-YSxo9OY(UUID, String, String)`）。 `BloodHorseId` 等の
 * ID 値クラスも同じく `UUID` に落ちるため、ArchUnit からは両者を区別できず 空振りする。ソースを見る detekt なら宣言上の型で判定できる。
 *
 * 対象は `domain.<context>.model..` の jMolecules `@Repository` 付き interface、または名前が `〜Repository` で終わる
 * interface（アノテーション付け忘れの取りこぼしを防ぐ #706 レビュー指摘）。加えて `application.<context>..` の `〜Queries`
 * interface。`<context> == iam` は対象外 （`Account` / `World` はテナントの根であり世界に属さない）。 `infrastructure` の
 * Spring Data リポジトリ（Spring の同名 `@Repository` が付く）はパッケージ判定で外れる。
 *
 * 既知の限界:
 * - ソースのテキストで判定するため `typealias W = WorldId` のような細工は素通りする（レビュー担保）。
 * - 対象集合は `@Repository` / `〜Repository`（domain）・`〜Queries` / `invoke`（application、
 *   [ActorScopedUseCase] も同様）という命名・アノテーション規約に依存する。この規約自体を機械強制する 仕組みは無いため、規約から外れた宣言（例: `〜Store`
 *   という名前で `@Repository` も付けない interface）は 静かに対象外になる。規約の遵守自体はレビューで担保する。
 * - `visitNamedFunction` のみを見るため、プロパティは対象外。Queries interface に `val allHorses:
 *   List<BloodHorseView>` のような引数の無い読み取りプロパティを生やすと検出されない。
 * - `WorldId?`（nullable）は適合と見なさない（型テキストの完全一致判定のため）。これは意図的な設計で、 nullable
 *   な世界スコープは設計上不要という前提に立ち、`trimEnd('?')` で黙認する方向へは倒さない。 誤って `WorldId?` にした場合は違反として報告されるため気づける（loud
 *   な失敗）。
 */
class WorldScopedPortSignature(config: Config) :
    Rule(
        config,
        "永続化ポート（Repository / Queries）は第 1 引数に WorldId を取ること。" +
            "データは世界ごとに閉じており、スコープは引数で運ぶ（ADR-0067）。",
    ) {
    override fun visitNamedFunction(function: KtNamedFunction) {
        super.visitNamedFunction(function)

        val declaration = function.containingClassOrObject as? KtClass ?: return
        if (!declaration.isInterface()) return
        if (!isWorldScopedPort(function.containingKtFile.packageFqName.asString(), declaration)) {
            return
        }
        if (function.valueParameters.firstOrNull()?.typeReference.denotesType("WorldId")) return

        report(
            Finding(
                Entity.from(function),
                "${declaration.name}.${function.name} は第 1 引数に WorldId を取っていない。" +
                    "永続化ポートは世界（テナント）のスコープを引数で受け取ること（ADR-0067 / #706）。",
            )
        )
    }

    /** 世界スコープを要求するポート（Repository ポート / クエリポート）の宣言かを判定する。 */
    private fun isWorldScopedPort(packageName: String, declaration: KtClass): Boolean {
        val (layer, context) = layerAndContext(packageName) ?: return false
        if (context == IAM_CONTEXT) return false
        return when (layer) {
            "domain" ->
                packageSegmentAfterLayer(packageName, 1) == "model" &&
                    (declaration.annotationEntries.any {
                        it.shortName?.asString() == "Repository"
                    } || declaration.name?.endsWith("Repository") == true)
            "application" -> declaration.name?.endsWith("Queries") == true
            else -> false
        }
    }
}
