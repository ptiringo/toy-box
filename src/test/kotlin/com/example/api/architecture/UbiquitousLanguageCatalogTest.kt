package com.example.api.architecture

import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaModifier
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import org.jmolecules.ddd.annotation.AggregateRoot
import org.jmolecules.ddd.annotation.Entity as DddEntity
import org.jmolecules.ddd.annotation.Repository as DddRepository
import org.jmolecules.ddd.annotation.ValueObject
import org.jmolecules.event.annotation.DomainEvent
import org.junit.jupiter.api.Test

/**
 * ユビキタス言語の「型レベル用語カタログ」を `docs/ubiquitous-language.md` の自動生成ブロックと突き合わせるテスト。
 *
 * 用語集は手書き（定義・別名・禁止語などコードに出ない知識）と、コードからの自動抽出（型レベルの実体）を 組み合わせて陳腐化を防ぐ方針（issue #346）。本テストは後者を担う:
 * jMolecules のビルディングブロック（`@AggregateRoot` / `@Entity` / `@ValueObject` / `@Repository` /
 * `@DomainEvent`）と ドメインサービス（`service/` のトップレベル関数）をバイトコードから走査し、コンテキスト別の用語カタログを生成して、
 * ドキュメントにコミットされた生成ブロックと一致することを検証する。
 *
 * コードが唯一の出所であり、ビルディングブロックを足し引きするとカタログが乖離してこのテストが落ちる。 再生成するには次を実行してドキュメントを更新し、差分をコミットする:
 * ```
 * ./gradlew test --tests "*UbiquitousLanguageCatalogTest" -DubiquitousLanguage.update=true
 * ```
 *
 * 定義・和名・別名・禁止語といった意味づけは手書きセクションが担い、本テストは型の一覧（どの用語が存在すべきか） だけをゲートする。KDoc はバイトコードに残らないため自動抽出の対象外。
 */
class UbiquitousLanguageCatalogTest {
    @Test
    fun `ユビキタス言語カタログがコードと一致すること`() {
        val expected = renderCatalog()
        val doc = locateDoc()
        val content = doc.readText()
        val current = extractGeneratedBlock(content)

        if (updateRequested) {
            doc.writeText(replaceGeneratedBlock(content, expected))
            return
        }

        assert(current.trim() == expected.trim()) {
            "ユビキタス言語カタログ（docs/ubiquitous-language.md の自動生成ブロック）がコードと乖離しています。" +
                "次で再生成してコミットしてください: ./gradlew test " +
                "--tests \"*UbiquitousLanguageCatalogTest\" -DubiquitousLanguage.update=true"
        }
    }

    private val updateRequested: Boolean
        get() = System.getProperty("ubiquitousLanguage.update") == "true"

    /** ドメイン層を走査して用語をコンテキスト別・種別順に並べた Markdown を生成する。 */
    private fun renderCatalog(): String {
        val terms = (buildingBlockTerms() + domainServiceTerms()).distinct()
        val byContext = terms.groupBy { it.context }.toSortedMap()

        return byContext.entries.joinToString(separator = "\n\n") { (context, contextTerms) ->
            val rows =
                contextTerms.sortedWith(compareBy({ it.kind.order }, { it.name })).joinToString(
                    separator = "\n"
                ) {
                    "| ${it.name} | ${it.kind.label} | ${it.pkg} |"
                }
            buildString {
                append("### $context\n\n")
                append("| 用語 | 種別 | パッケージ |\n")
                append("| --- | --- | --- |\n")
                append(rows)
            }
        }
    }

    /** jMolecules アノテーションで役割を表明したビルディングブロックを抽出する。 */
    private fun buildingBlockTerms(): List<Term> = importedClasses.mapNotNull { javaClass ->
        val context = contextOf(javaClass.packageName) ?: return@mapNotNull null
        val kind =
            when {
                javaClass.isAnnotatedWith(AggregateRoot::class.java) -> Kind.AGGREGATE_ROOT
                javaClass.isAnnotatedWith(DddEntity::class.java) -> Kind.ENTITY
                javaClass.isAnnotatedWith(ValueObject::class.java) -> Kind.VALUE_OBJECT
                javaClass.isAnnotatedWith(DddRepository::class.java) -> Kind.REPOSITORY
                javaClass.isAnnotatedWith(DomainEvent::class.java) -> Kind.DOMAIN_EVENT
                else -> return@mapNotNull null
            }
        Term(context, kind, displayName(javaClass), relativePackage(javaClass.packageName))
    }

    /**
     * ドメインサービス（`service/` のトップレベル関数）を抽出する。
     *
     * トップレベル関数はファイルごとのファサードクラス（`〜Kt`）の public static メソッドへコンパイルされる。 失敗バリアント型（`〜Error`）は別クラスなので Kt
     * クラスのメソッドには現れない。デフォルト引数が生む `〜$default` 等の合成メソッドは名前に `$` を含むため除外する。 inline value class
     * を引数・戻り値に持つ関数は JVM メソッド名が `関数名-<ハッシュ>` にマングルされるため、 Kotlin 識別子に使えない `-` 以降を落として実名へ戻す。
     */
    private fun domainServiceTerms(): List<Term> =
        importedClasses
            .filter { it.packageName.contains(".service") && it.simpleName.endsWith("Kt") }
            .flatMap { facade ->
                val context = contextOf(facade.packageName) ?: return@flatMap emptyList<Term>()
                facade.methods
                    .filter { method ->
                        method.modifiers.containsAll(
                            listOf(JavaModifier.PUBLIC, JavaModifier.STATIC)
                        ) && !method.name.contains('$')
                    }
                    .map {
                        Term(
                            context,
                            Kind.DOMAIN_SERVICE,
                            it.name.substringBefore('-'),
                            relativePackage(facade.packageName),
                        )
                    }
            }

    /** ネストした型は `囲み型.型名` で表す（例: `FoalingOutcome.LiveFoal`）。 */
    private fun displayName(javaClass: JavaClass): String {
        val enclosing = javaClass.enclosingClass.orElse(null) ?: return javaClass.simpleName
        return "${enclosing.simpleName}.${javaClass.simpleName}"
    }

    private fun relativePackage(packageName: String): String = packageName.removePrefix("$BASE.")

    /** ドメイン層のパッケージ名から境界づけられたコンテキスト名を取り出す。`shared` は共有カーネルなので対象外。 */
    private fun contextOf(packageName: String): String? {
        val context = DOMAIN_CONTEXT.matchEntire(packageName)?.groupValues?.get(1)
        return context?.takeUnless { it == "shared" }
    }

    private data class Term(val context: String, val kind: Kind, val name: String, val pkg: String)

    private enum class Kind(val order: Int, val label: String) {
        AGGREGATE_ROOT(0, "集約ルート"),
        ENTITY(1, "エンティティ"),
        VALUE_OBJECT(2, "値オブジェクト"),
        REPOSITORY(3, "リポジトリポート"),
        DOMAIN_EVENT(4, "ドメインイベント"),
        DOMAIN_SERVICE(5, "ドメインサービス"),
    }

    private companion object {
        const val BASE = "com.example.api"
        val DOMAIN_CONTEXT = Regex("""com\.example\.api\.domain\.([^.]+)(?:\..*)?""")
        const val BEGIN_MARKER = "<!-- BEGIN GENERATED:ubiquitous-language -->"
        const val END_MARKER = "<!-- END GENERATED:ubiquitous-language -->"

        val importedClasses =
            ClassFileImporter()
                .withImportOption(ImportOption.DoNotIncludeTests())
                .importPackages(BASE)

        /** ドキュメントは Gradle のプロジェクトディレクトリ基準で解決する。見つからなければ親を辿る。 */
        fun locateDoc(): Path {
            var dir: Path? = Path.of("").toAbsolutePath()
            while (dir != null) {
                val candidate = dir.resolve("docs/ubiquitous-language.md")
                if (candidate.exists()) return candidate
                dir = dir.parent
            }
            error("docs/ubiquitous-language.md が見つかりません")
        }

        fun blockRegex(): Regex =
            Regex(
                "${Regex.escape(BEGIN_MARKER)}.*?${Regex.escape(END_MARKER)}",
                RegexOption.DOT_MATCHES_ALL,
            )

        fun extractGeneratedBlock(content: String): String {
            val match =
                blockRegex().find(content)
                    ?: error("自動生成ブロックのマーカー（$BEGIN_MARKER 〜 $END_MARKER）が見つかりません")
            return match.value.removePrefix(BEGIN_MARKER).removeSuffix(END_MARKER)
        }

        fun replaceGeneratedBlock(content: String, generated: String): String =
            blockRegex().replace(content, "$BEGIN_MARKER\n\n$generated\n\n$END_MARKER")
    }
}
