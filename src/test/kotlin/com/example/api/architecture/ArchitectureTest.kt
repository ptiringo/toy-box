package com.example.api.architecture

import com.example.api.ApiApplication
import com.tngtech.archunit.base.DescribedPredicate.not
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.library.Architectures.onionArchitecture
import com.tngtech.archunit.library.GeneralCodingRules
import com.tngtech.archunit.library.dependencies.SliceAssignment
import com.tngtech.archunit.library.dependencies.SliceIdentifier
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices
import org.jmolecules.archunit.JMoleculesDddRules
import org.jmolecules.ddd.annotation.AggregateRoot
import org.jmolecules.ddd.annotation.Entity as DddEntity
import org.jmolecules.ddd.annotation.Repository as DddRepository
import org.jmolecules.ddd.annotation.ValueObject
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
 * 境界づけられたコンテキスト（horseracing / sakamichi / tennis 等）へのスライス割り当て。
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

    /** HTTP アダプター（@RestController）は controller 層に置くこと。 */
    @ArchTest
    val restControllersResideInControllerLayer =
        classes()
            .that()
            .areAnnotatedWith(RestController::class.java)
            .should()
            .resideInAPackage(CONTROLLER)

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
}
