package com.example.api.architecture

import com.example.api.ApiApplication
import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.base.DescribedPredicate.not
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods
import com.tngtech.archunit.library.Architectures.onionArchitecture
import com.tngtech.archunit.library.GeneralCodingRules
import com.tngtech.archunit.library.dependencies.SliceAssignment
import com.tngtech.archunit.library.dependencies.SliceIdentifier
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices
import java.util.UUID
import org.jmolecules.archunit.JMoleculesDddRules
import org.jmolecules.ddd.annotation.AggregateRoot
import org.jmolecules.ddd.annotation.Entity as DddEntity
import org.jmolecules.ddd.annotation.Repository as DddRepository
import org.jmolecules.ddd.annotation.ValueObject
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.RestController

private const val DOMAIN = "com.example.api.domain.."
// 共有カーネル（building block 基盤）。最内核としてドメインモデルリングに含める。
private const val DOMAIN_SHARED = "com.example.api.domain.shared.."
// 各コンテキストのドメインモデル（Entity / Value Object / Repository ポート）。
private const val DOMAIN_MODEL = "com.example.api.domain..model.."
// 各コンテキストのドメインサービス（モデルを組み合わせる、フレームワーク非依存のロジック）。
private const val DOMAIN_SERVICE = "com.example.api.domain..service.."
private const val APPLICATION = "com.example.api.application.."
private const val CONTROLLER = "com.example.api.controller.."
private const val INFRASTRUCTURE = "com.example.api.infrastructure.."

/**
 * ドメインサービスの失敗バリアント型（`〜Error` の sealed interface とその variant）であること。
 *
 * サービスの戻り値（`Result<_, 〜Error>`）の失敗側を表す型はサービスと同じパッケージに同居させており、 これは `object` / `class`
 * によるサービスのラップではないため [domainServicesAreTopLevelFunctions] の対象から除外する。ネストした variant も囲みクラスを辿って判定する。
 */
private val isFailureVariantType =
    DescribedPredicate.describe<JavaClass>("ドメインサービスの失敗バリアント型（〜Error）") { javaClass ->
        generateSequence(javaClass) { it.enclosingClass.orElse(null) }
            .any { it.simpleName.endsWith("Error") }
    }

/**
 * ドメイン層（`com.example.api.domain..`）に属する enum であること。
 *
 * HTTP 契約の DTO がフィールド型にドメイン enum を持つことを禁じる [dtosDoNotExposeDomainEnums] で用いる。 enum
 * かつドメインパッケージ配下のものだけを対象とし、`controller` 層の契約専用 enum（`〜Dto`）は対象外とする。
 */
private val isDomainEnum =
    DescribedPredicate.describe<JavaClass>("ドメイン層の enum") { javaClass ->
        javaClass.isEnum && javaClass.packageName.startsWith("com.example.api.domain.")
    }

/**
 * 境界づけられたコンテキスト（studbook / racing / sakamichi / tennis 等）へのスライス割り当て。
 *
 * application / domain / infrastructure 各層の直下のパッケージ名をコンテキスト名とみなす。 domain
 * 直下の共有カーネル（`shared`、[com.example.api.domain.shared.Command] 等）、controller、
 * ルートパッケージはコンテキストに属さないため対象外とする。
 */
private object BoundedContextAssignment : SliceAssignment {
    private val contextPackage =
        Regex("""com\.example\.api\.(?:application|domain|infrastructure)\.([^.]+)(?:\..*)?""")

    override fun getIdentifierOf(javaClass: JavaClass): SliceIdentifier {
        val context = contextPackage.matchEntire(javaClass.packageName)?.groupValues?.get(1)
        return if (context == null || context == "shared") {
            SliceIdentifier.ignore()
        } else {
            SliceIdentifier.of(context)
        }
    }

    override fun getDescription(): String = "境界づけられたコンテキスト"
}

/**
 * アーキテクチャ規約を強制するテスト。
 *
 * 規約の全体像と意図は `.claude/rules/architecture.md` を参照。
 */
@AnalyzeClasses(
    packagesOf = [ApiApplication::class],
    importOptions = [ImportOption.DoNotIncludeTests::class],
)
class ArchitectureTest {
    /**
     * オニオンアーキテクチャの依存方向に従うこと。
     *
     * 内側から domainModel（共有カーネル + 各コンテキストの model）← domainService ← applicationService ←
     * adapter（controller / infrastructure）。ドメインサービスはモデルにのみ依存でき、その逆は禁止。 アダプター同士の参照も禁止される。
     */
    @ArchTest
    val onionLayers =
        onionArchitecture()
            .domainModels(DOMAIN_SHARED, DOMAIN_MODEL)
            .domainServices(DOMAIN_SERVICE)
            .applicationServices(APPLICATION)
            .adapter("rest", CONTROLLER)
            .adapter("persistence", INFRASTRUCTURE)

    /** domain 層はフレームワークに依存しないこと。 */
    @ArchTest
    val domainIsFrameworkFree =
        noClasses()
            .that()
            .resideInAPackage(DOMAIN)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework..", "jakarta..", "com.fasterxml.jackson..")

    /**
     * ドメインサービス（`domain.*.service`）は Kotlin のトップレベル関数で書くこと。
     *
     * `object` / `class` でラップせず、`service/` パッケージへの配置でドメインサービスを表現する
     * （`.claude/rules/architecture.md`）。Kotlin のトップレベル関数はファイルごとのファサードクラス（`〜Kt`）へ コンパイルされるため、service
     * パッケージ内のクラスが `Kt` で終わることを検証して `object` / `class` 宣言を排除する。
     * ただしサービスの戻り値の失敗側を表す失敗バリアント型（`〜Error`）はサービスと同居させてよく、対象から除外する。
     */
    @ArchTest
    val domainServicesAreTopLevelFunctions =
        classes()
            .that()
            .resideInAPackage(DOMAIN_SERVICE)
            .and(not(isFailureVariantType))
            .should()
            .haveSimpleNameEndingWith("Kt")
            .because("ドメインサービスは object / class でラップせずトップレベル関数で書く。" + "service/ への配置でドメインサービスを表現する")

    /** application 層の Spring 依存は DI 用 stereotype アノテーションのみに留めること。 */
    @ArchTest
    val applicationDependsOnSpringOnlyForDi =
        noClasses()
            .that()
            .resideInAPackage(APPLICATION)
            .should()
            .dependOnClassesThat(
                resideInAPackage("org.springframework..")
                    .and(not(resideInAPackage("org.springframework.stereotype..")))
            )

    /** 境界づけられたコンテキスト同士は依存しないこと。 */
    @ArchTest
    val boundedContextsAreIsolated =
        slices().assignedFrom(BoundedContextAssignment).should().notDependOnEachOther()

    /**
     * jMolecules で表明した DDD ビルディングブロックの整合性。
     *
     * 集約間の参照は ID（または Association）経由のみ、Entity / AggregateRoot は識別子を持つ、 ValueObject は Entity
     * 系を参照しない、等を検証する。
     */
    @ArchTest val dddBuildingBlocks = JMoleculesDddRules.all()

    /** DDD ビルディングブロック（jMolecules アノテーション付きクラス）はドメインモデルリングに置くこと。 */
    @ArchTest
    val dddBuildingBlocksResideInDomainModel =
        classes()
            .that()
            .areAnnotatedWith(AggregateRoot::class.java)
            .or()
            .areAnnotatedWith(DddEntity::class.java)
            .or()
            .areAnnotatedWith(ValueObject::class.java)
            .or()
            .areAnnotatedWith(DddRepository::class.java)
            .should()
            .resideInAPackage(DOMAIN_MODEL)

    /**
     * 集約（@AggregateRoot / @Entity）はイミュータブルに保つこと（val のみ・var 禁止）。
     *
     * 状態遷移は対象を書き換えず、同一性（ID）を引き継いだ新インスタンスを返すメソッドで表す（ADR-0009）。 Kotlin の `val` プロパティは final
     * フィールドへ、`var` は非 final フィールド（＋ setter）へコンパイルされるため、 集約クラスが直接宣言するフィールドが全て final であることを検証することで
     * `var` を排除する。
     */
    @ArchTest
    val aggregatesAreImmutable =
        fields()
            .that()
            .areDeclaredInClassesThat()
            .areAnnotatedWith(AggregateRoot::class.java)
            .or()
            .areDeclaredInClassesThat()
            .areAnnotatedWith(DddEntity::class.java)
            .should()
            .beFinal()
            .because("集約はイミュータブルに保ち、状態遷移は新インスタンスで表す（ADR-0009）。var は禁止")

    /**
     * 集約（@AggregateRoot / @Entity）は `data class` を使わないこと。
     *
     * `data class` は全プロパティから `equals` / `hashCode` を生成するため、ID ベースの `final equals` / `hashCode`
     * （[EntityTest] 参照）と衝突する。状態遷移は `private constructor` ＋手書き `copy` で同一性（ID）を引き継いだ新インスタンスとして
     * 写像する（ADR-0009）。Kotlin の `data class` は各プロパティに `componentN()` を生成するため、集約クラスが
     * `componentN()`（`component1` / `component2` …）を持たないことを検証して `data class` を排除する。手書き `copy` は
     * `componentN()` を生成しないため誤検出されない。
     */
    @ArchTest
    val aggregatesAreNotDataClasses =
        noMethods()
            .that()
            .areDeclaredInClassesThat()
            .areAnnotatedWith(AggregateRoot::class.java)
            .or()
            .areDeclaredInClassesThat()
            .areAnnotatedWith(DddEntity::class.java)
            .should()
            .haveNameMatching("component\\d+")
            .because(
                "集約は data class を使わない。ID ベースの final equals / hashCode と衝突するため、" +
                    "private constructor ＋手書き copy で同一性を引き継いだ新インスタンスへ写像する（ADR-0009）"
            )

    /** HTTP アダプター（@RestController）は controller 層に置くこと。 */
    @ArchTest
    val restControllersResideInControllerLayer =
        classes()
            .that()
            .areAnnotatedWith(RestController::class.java)
            .should()
            .resideInAPackage(CONTROLLER)

    /**
     * `@RestController` のハンドラは成功レスポンスで `ResponseEntity` を返さないこと。
     *
     * 成功レスポンスは `@ResponseStatus` ＋戻り値で resource を返す（`.claude/rules/error-handling.md` /
     * `.claude/rules/api-design.md`）。エラー描画 funnel の `GlobalExceptionHandler` は `@RestController`
     * ではない（`ResponseEntityExceptionHandler` 継承）ため、本ルールでは誤検出されない。
     */
    @ArchTest
    val restControllersDoNotReturnResponseEntity =
        noMethods()
            .that()
            .areDeclaredInClassesThat()
            .areAnnotatedWith(RestController::class.java)
            .should()
            .haveRawReturnType(ResponseEntity::class.java)
            .because("成功レスポンスは @ResponseStatus ＋戻り値で resource を返す。ResponseEntity は使わない")

    /**
     * HTTP 契約（request / response DTO）はフィールド型にドメイン enum を持たないこと。
     *
     * ドメイン enum を wire に直接晒すと、ドメイン側の列挙子リネームが HTTP 契約（生成クライアント含む）を無言で破壊する。 これを断つため `controller`
     * 層に契約専用の `〜Dto` enum を置き、`toDomain()` / `toApi()` の網羅 `when`
     * で相互変換する（`.claude/rules/api-design.md` / ADR-0007）。マッパー関数はドメイン enum をメソッドの引数・戻り値で扱うが、
     * 本ルールは**フィールド型**のみを対象とするため誤検出されない。
     */
    @ArchTest
    val dtosDoNotExposeDomainEnums =
        noFields()
            .that()
            .areDeclaredInClassesThat()
            .resideInAPackage(CONTROLLER)
            .should()
            .haveRawType(isDomainEnum)
            .because(
                "HTTP 契約はドメイン enum を wire に直接晒さず、controller 層の 〜Dto enum へ" +
                    "マッピングする（ADR-0007）。列挙子リネームによる契約破壊を防ぐ"
            )

    /** ユースケース（@Service）は application 層に置くこと。 */
    @ArchTest
    val servicesResideInApplicationLayer =
        classes()
            .that()
            .areAnnotatedWith(Service::class.java)
            .should()
            .resideInAPackage(APPLICATION)

    /** Repository ポートの実装（Spring の @Repository）は infrastructure 層に置くこと。 */
    @ArchTest
    val repositoryImplementationsResideInInfrastructure =
        classes()
            .that()
            .areAnnotatedWith(Repository::class.java)
            .should()
            .resideInAPackage(INFRASTRUCTURE)

    /** 標準出力・標準エラーへ直接書き込まないこと。 */
    @ArchTest val noStandardStreams = GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS

    /** フィールドインジェクションを使わずコンストラクタインジェクションを使うこと。 */
    @ArchTest val noFieldInjection = GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION

    /**
     * ID は `UUID.randomUUID()` を直接呼ばず `domain.shared.generateId()`（UUIDv7 相当のタイムベース生成）経由で生成すること。
     *
     * 生成値が時刻順にソート可能で永続化時のインデックス局所性に優れるため、全 ID をタイムベース生成に統一する（ADR-0005）。
     */
    @ArchTest
    val idsAreGeneratedViaGenerateId =
        noClasses()
            .should()
            .callMethod(UUID::class.java, "randomUUID")
            .because(
                "ID は domain.shared.generateId()（UUIDv7 相当のタイムベース生成）経由で生成する。" +
                    "永続化時のインデックス局所性のため UUID.randomUUID() の直接呼び出しは禁止（ADR-0005）"
            )
}
