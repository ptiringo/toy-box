package com.example.api.architecture

import com.example.api.ApiApplication
import com.example.api.domain.shared.Actor
import com.example.api.domain.shared.Command
import com.tngtech.archunit.base.DescribedPredicate.not
import com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage
import com.tngtech.archunit.core.domain.JavaClass.Predicates.type
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.library.Architectures.onionArchitecture
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

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
            .adapter("mcp", MCP)

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

    /**
     * application 層の Spring 依存は「配線の語彙」の精密 allowlist に留めること。
     *
     * 許可するのは DI 用 stereotype（`org.springframework.stereotype..`）、宣言的トランザクション境界
     * （`org.springframework.transaction.annotation..`。`@Transactional` 等はメタデータのみで実行機構への
     * 依存ではないため許可する。`TransactionTemplate` / `PlatformTransactionManager` 等の実行機構
     * （`org.springframework.transaction` 直下・`.support`）は引き続き禁止する。ADR-0051）、
     * ドメインイベント発行（[ApplicationEventPublisher] クラス単位。`org.springframework.context..` を パッケージごと開けて
     * `ApplicationContext` 等への依存が紛れ込むのを防ぐ）のみ。業務ロジックを Spring API に依存させないための制限（ADR-0050）。
     */
    @ArchTest
    val applicationDependsOnSpringOnlyForWiring =
        noClasses()
            .that()
            .resideInAPackage(APPLICATION)
            .should()
            .dependOnClassesThat(
                resideInAPackage("org.springframework..")
                    .and(not(resideInAPackage("org.springframework.stereotype..")))
                    .and(not(resideInAPackage("org.springframework.transaction.annotation..")))
                    .and(not(type(ApplicationEventPublisher::class.java)))
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

    /**
     * 書き込みユースケース（`Command` を受ける `invoke`）はトランザクション境界（`@Transactional`）を持つこと。
     *
     * 複数集約を書き込むユースケースでインフラ障害時の原子性が欠ける（先行 save が孤児として残る）ことを 構造的に防ぐ（#483 / ADR-0051）。入力 DTO
     * 規約（書き込み=`Command` 封筒 / 読み取り=`〜Query`）により 書き込み系を静的に判別する。読み取り系ユースケースは対象外（readOnly
     * トランザクションは導入しない）。
     *
     * `invoke` は完全一致 (`haveName`) ではなく前方一致 (`haveNameStartingWith`) で照合する。戻り値 `Result<V,
     * E>`（kotlin-result）が inline value class のため、Kotlin コンパイラがプラットフォーム宣言の衝突回避で メソッド名をマングルする（例:
     * `invoke-Zyo9ksc`）。完全一致では実バイトコード名と食い違い空振りする （ミューテーション検証で確認済み）。
     *
     * 前方一致に加えて `haveRawParameterTypes(Actor, Command)` を AND 条件に置くことで、前方一致の広すぎる当たりを 「認可の主体 `Actor`
     * と `Command` 封筒を受け取る書き込み `invoke`」だけに絞り安全にしている。#606 以降、書き込み ユースケースは第 1 引数に認可の主体 `Actor`、第 2
     * 引数に `Command` 封筒を取る（`invoke(actor, command)`）ため、 対象は `[Actor, Command]` の 2 引数 `invoke`
     * に固定される。裏返すと、この形を採らない書き込みメソッドはこの ガードの対象外（規約＝「書き込みは `Actor` ＋ `Command` を取る `invoke`」に依存する）。
     */
    @ArchTest
    val commandHandlingInvokesAreTransactional =
        methods()
            .that()
            .areDeclaredInClassesThat()
            .resideInAPackage(APPLICATION)
            .and()
            .haveNameStartingWith("invoke")
            .and()
            .haveRawParameterTypes(Actor::class.java, Command::class.java)
            .should()
            .beAnnotatedWith(Transactional::class.java)
}
