package com.example.api.architecture

import com.example.api.ApiApplication
import com.example.api.domain.shared.Actor
import com.example.api.mcp.McpActorFactory
import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaCall
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaConstructorCall
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.jmolecules.ddd.annotation.Repository as DddRepository

/**
 * MCP アダプタ（`com.example.api.mcp`）のテナント分離に関する規約を強制するテスト（#752）。
 *
 * MCP には認証が無く、テナント分離は「`Actor` は必ず [McpActorFactory] が組む」1 点に懸かっている（ADR-0073）。 [McpActorFactory] は
 * `accountId` を設定から解決し、ツール引数の `worldId` を `existsOwnedBy` で所有確認してから [Actor] を返す。ツールがこの経路を迂回できる書き方を
 * 2 つ塞ぐ（3 つ目の「ツール引数に `accountId` 相当を足す」は、 公開される入力スキーマ側で `McpServerWiringTest` が検査する）。
 *
 * メタテストは [McpAdapterRuleTest] にある。
 */
@AnalyzeClasses(
    packagesOf = [ApiApplication::class],
    importOptions = [ImportOption.DoNotIncludeTests::class],
)
class McpAdapterRulesTest {
    /**
     * MCP アダプタで [Actor] を組めるのは [McpActorFactory] だけ。
     *
     * `resolveAccountId()` で得た `AccountId` と任意の `WorldId` から `Actor(...)` を直接組むと、所有確認
     * （`existsOwnedBy`）を飛ばして他人の世界を操作できる。既存ツールからのコピペで最も踏みやすい形なので塞ぐ。 `actor.copy(worldId = …)`
     * でも同じことができるため、コンストラクタに加えて `copy` も禁じる。
     */
    @ArchTest val actorIsBuiltOnlyByFactory = actorIsBuiltOnlyByFactory(MCP)

    /**
     * MCP アダプタで永続化ポート（jMolecules の `@Repository` ／ application の `〜Queries`）に依存してよいのは
     * [McpActorFactory] だけ。
     *
     * ポートは第 1 引数に `WorldId` を取るため、ツールが直接注入すると任意の UUID を渡して所有確認を迂回できる。 ツールはユースケース（`Actor`
     * を受け取る）を経由する。
     */
    @ArchTest val toolsDoNotDependOnPersistencePorts = toolsDoNotDependOnPersistencePorts(MCP)
}

/** [Actor] のコンストラクタ、または `copy`（`copy$default` を含む）の呼び出し。 */
private val constructsOrCopiesActor =
    DescribedPredicate.describe<JavaCall<*>>("Actor のコンストラクタまたは copy の呼び出し") { call ->
        val target = call.target
        // value class を引数に取るため copy の JVM 名は `copy-<hash>` にマングルされる（architecture.md の空振りの節）
        target.owner.isEquivalentTo(Actor::class.java) &&
            (call is JavaConstructorCall || target.name.substringBefore('-') == "copy")
    }

/** jMolecules の `@Repository` 付きポート、または application 層の `〜Queries` ポート。 */
private val isPersistencePort =
    DescribedPredicate.describe<JavaClass>("永続化ポート（@Repository / 〜Queries）") { javaClass ->
        javaClass.isAnnotatedWith(DddRepository::class.java) ||
            (javaClass.packageName.startsWith("com.example.api.application.") &&
                javaClass.isInterface &&
                javaClass.simpleName.endsWith("Queries"))
    }

/**
 * [packageIdentifier] 配下で [McpActorFactory] 以外が [Actor] を組まないことを検証するルールを組む。
 *
 * 対象パッケージを引数に取るのは、メタテストが mcp の外に置いた違反フィクスチャへ同じルールを当てるため。本番は [MCP] を渡す。
 */
internal fun actorIsBuiltOnlyByFactory(packageIdentifier: String): ArchRule =
    noClasses()
        .that()
        .resideInAPackage(packageIdentifier)
        .and()
        .doNotBelongToAnyOf(McpActorFactory::class.java)
        .should()
        .callCodeUnitWhere(constructsOrCopiesActor)
        .because(
            "MCP では Actor を McpActorFactory だけが組む。" +
                "直接組むと所有確認（existsOwnedBy）を迂回できる（#752 / ADR-0073）"
        )

/**
 * [packageIdentifier] 配下で [McpActorFactory] 以外が永続化ポートに依存しないことを検証するルールを組む。 対象パッケージを引数に取る理由は
 * [actorIsBuiltOnlyByFactory] と同じ。
 */
internal fun toolsDoNotDependOnPersistencePorts(packageIdentifier: String): ArchRule =
    noClasses()
        .that()
        .resideInAPackage(packageIdentifier)
        .and()
        .doNotBelongToAnyOf(McpActorFactory::class.java)
        .should()
        .dependOnClassesThat(isPersistencePort)
        .because("MCP ツールはユースケース経由で操作する。ポートを直接使うと WorldId に任意の値を渡せる（#752 / ADR-0073）")
