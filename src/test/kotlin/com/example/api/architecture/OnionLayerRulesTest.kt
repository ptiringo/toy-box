package com.example.api.architecture

import com.example.api.ApiApplication
import com.tngtech.archunit.base.DescribedPredicate.not
import com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.library.Architectures.onionArchitecture
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service

/**
 * オニオンアーキテクチャの 4 リングに関する規約を強制するテスト。
 *
 * 依存方向・ドメイン層のフレームワーク非依存・ドメインサービスの書き方・各レイヤーの stereotype 配置を検証する。 規約の全体像と意図は
 * `.claude/rules/architecture.md` を参照。共有部品（レイヤー定数等）は [ArchSupport] にある。
 */
@AnalyzeClasses(
    packagesOf = [ApiApplication::class],
    importOptions = [ImportOption.DoNotIncludeTests::class],
)
class OnionLayerRulesTest {
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
}
