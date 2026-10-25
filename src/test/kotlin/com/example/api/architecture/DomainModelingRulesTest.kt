package com.example.api.architecture

import com.example.api.ApiApplication
import com.example.api.domain.shared.Entity
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods
import org.jmolecules.architecture.cqrs.QueryModel
import org.jmolecules.archunit.JMoleculesDddRules
import org.jmolecules.ddd.annotation.AggregateRoot
import org.jmolecules.ddd.annotation.Entity as DddEntity
import org.jmolecules.ddd.annotation.Repository as DddRepository
import org.jmolecules.ddd.annotation.ValueObject
import org.jmolecules.event.annotation.DomainEvent

/**
 * ドメインモデリング（DDD ビルディングブロック）に関する規約を強制するテスト。
 *
 * jMolecules で表明した役割の整合性・ビルディングブロックの配置・集約のイミュータビリティ・読み取りモデルの配置を 検証する。規約の全体像と意図は
 * `.claude/rules/architecture.md` を参照。メタテストは [AggregateNotDataClassRuleTest] /
 * [BuildingBlockAnnotationRuleTest] にある。
 */
@AnalyzeClasses(
    packagesOf = [ApiApplication::class],
    importOptions = [ImportOption.DoNotIncludeTests::class],
)
class DomainModelingRulesTest {
    /**
     * jMolecules で表明した DDD ビルディングブロックの整合性。
     *
     * 集約間の参照は ID（または Association）経由のみ、Entity / AggregateRoot は識別子を持つ、 ValueObject は Entity
     * 系を参照しない、等を検証する。
     */
    @ArchTest val dddBuildingBlocks = JMoleculesDddRules.all()

    /**
     * [Entity] を継承する型は `@AggregateRoot` か `@Entity` を付けること（#737）。
     *
     * [dddBuildingBlocksResideInDomainModel] は「アノテーションが付いている → domain.*.model に居ろ」の向きしか
     * 課さないため、付け忘れた集約は静かに DDD ビルディングブロックの外へ落ち、[dddBuildingBlocks] の検査（識別子の必須化・ 集約間参照の ID
     * 化など）からも漏れる。ID 同一性と楽観ロックの基底である [Entity] の継承は集約・エンティティにしか 現れない構造上の手がかりなので、これを入口に逆向きを課す。
     */
    @ArchTest
    val entitySubtypesAreAnnotatedAsAggregateOrEntity =
        classes()
            .that()
            .areAssignableTo(Entity::class.java)
            .and()
            .doNotHaveFullyQualifiedName(Entity::class.java.name)
            .should()
            .beAnnotatedWith(AggregateRoot::class.java)
            .orShould()
            .beAnnotatedWith(DddEntity::class.java)
            .because("Entity を継承する型は集約かエンティティであり、jMolecules で役割を表明する（#737）")

    /**
     * ドメインの inline value class は `@ValueObject` を付けること（#737）。
     *
     * ドメインの `@JvmInline value class` は ID 値クラスを含めて値オブジェクトとしてだけ使っている（architecture.md の DDD
     * ビルディングブロック表）。付け忘れると [dddBuildingBlocks] の「値オブジェクトは集約を参照しない」等から漏れる。
     */
    @ArchTest val inlineValueClassesAreValueObjects = inlineValueClassesAreValueObjects(DOMAIN)

    /**
     * ドメインモデルの `〜Repository` interface は jMolecules の `@Repository` を付けること（#737）。
     *
     * 永続化ポートは名前を `〜Repository` とする規約で、#706 の `WorldScopedPortSignature`（detekt）も同じ名前を 対象判定の入口の 1
     * つにしている。アノテーションを付け忘れると [dddBuildingBlocks] とユビキタス言語カタログから漏れる。
     */
    @ArchTest val repositoryPortsAreAnnotated = repositoryPortsAreAnnotated(DOMAIN_MODEL)

    /**
     * 値オブジェクト以外の DDD ビルディングブロックはドメインモデルリングに置くこと。
     *
     * 集約ルート / エンティティ / リポジトリポートに加え、ドメインイベント（`@DomainEvent`）も対象に含める。 イベントもドメインモデルの一員であり、フレームワーク非依存の
     * domain.*.model に置く（ADR-0029）。
     */
    @ArchTest
    val dddBuildingBlocksResideInDomainModel =
        classes()
            .that()
            .areAnnotatedWith(AggregateRoot::class.java)
            .or()
            .areAnnotatedWith(DddEntity::class.java)
            .or()
            .areAnnotatedWith(DddRepository::class.java)
            .or()
            .areAnnotatedWith(DomainEvent::class.java)
            .should()
            .resideInAPackage(DOMAIN_MODEL)

    /**
     * 値オブジェクトはドメインモデルリング、または共有カーネル（`domain.shared`）に置くこと。
     *
     * 共有カーネルを許すのは値オブジェクトだけに限る。`AccountId` / `WorldId` のように全コンテキストが読む
     * 横断的な値は、コンテキスト間依存が全面禁止されている以上ここにしか置けない。一方で集約・リポジトリ ポートまで共有カーネルに置けるようにすると、各コンテキストの語彙が shared
     * へ流れ込んで腐る。
     */
    @ArchTest
    val valueObjectsResideInDomainModelOrSharedKernel =
        classes()
            .that()
            .areAnnotatedWith(ValueObject::class.java)
            .should()
            .resideInAnyPackage(DOMAIN_MODEL, DOMAIN_SHARED)

    /**
     * 読み取りモデル（`@QueryModel`）は application 層に置くこと。
     *
     * 軽量 CQRS（L2）の読み取り側として、Read Model（View）は書き込み集約を経由せずストアから直接組む フラットな DTO である（ADR-0031）。DDD
     * ビルディングブロック（`@AggregateRoot` 等）が domain.*.model に
     * 居る（[dddBuildingBlocksResideInDomainModel]）のと対称に、読みモデルはドメインを汚さず application 層へ
     * 置く。`@QueryModel` は jMolecules の CQRS アーキテクチャ注釈（`org.jmolecules.architecture.cqrs`）であり DDD
     * ビルディングブロック注釈ではないため、[dddBuildingBlocksResideInDomainModel] とは独立して検証する。
     */
    @ArchTest
    val queryModelsResideInApplication =
        classes()
            .that()
            .areAnnotatedWith(QueryModel::class.java)
            .should()
            .resideInAPackage(APPLICATION)
            .because("読み取りモデル（@QueryModel）は軽量 CQRS（L2）の読み取り側として application 層に置く（ADR-0031）")

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

    /**
     * `Entity.version`（楽観ロックの永続化メタデータ）を業務判断（分岐・比較）に使わないこと。
     *
     * domain / application 層のプロダクションコードから、自クラス以外の Entity サブタイプの `version` を読む getter
     * 呼び出しを禁止する（ADR-0047。Kotlin のプロパティ読み取りはバイトコードでは `getVersion()` 呼び出しになる）。集約自身の手書き `copy` の
     * `version = version` 引き回しは自己参照 として対象外。infrastructure はマッパーが正当に読むため対象外とする。
     *
     * 既知の限界（レビュー担保）: 集約が自分自身の `version` で業務分岐する誤用は `copy` の自己参照と
     * 区別できず検出できない。プロパティ参照（`::version`）・リフレクション経由も検出外。 ルールが実際に違反を検出することは [EntityVersionReadRuleTest]
     * で別途担保する。
     */
    @ArchTest
    val versionIsNotUsedForBusinessDecisions =
        noClasses()
            .that()
            .resideInAnyPackage(DOMAIN, APPLICATION)
            .should()
            .callMethodWhere(readsEntityVersionOfAnotherClass)
            .because("version は楽観ロックの永続化メタデータであり、業務判断（分岐・比較）に使わない（ADR-0047）")
}

/**
 * [packageIdentifier] 配下の `@JvmInline value class` が `@ValueObject` を付けていることを検証するルールを組む。
 *
 * 対象パッケージを引数に取るのは、メタテスト（[BuildingBlockAnnotationRuleTest]）がドメイン外に置いた違反フィクスチャへ同じ ルールを当てるため。本番の
 * `@ArchTest` は [DOMAIN] を渡す。
 */
internal fun inlineValueClassesAreValueObjects(packageIdentifier: String): ArchRule =
    classes()
        .that()
        .resideInAPackage(packageIdentifier)
        .and()
        .areAnnotatedWith(JvmInline::class.java)
        .should()
        .beAnnotatedWith(ValueObject::class.java)
        .because("ドメインの inline value class は値オブジェクトであり、jMolecules で役割を表明する（#737）")

/**
 * [packageIdentifier] 配下で名前が `Repository` で終わる interface が jMolecules の `@Repository` を付けていることを
 * 検証するルールを組む。対象パッケージを引数に取る理由は [inlineValueClassesAreValueObjects] と同じ。本番は [DOMAIN_MODEL] を渡す。
 */
internal fun repositoryPortsAreAnnotated(packageIdentifier: String): ArchRule =
    classes()
        .that()
        .resideInAPackage(packageIdentifier)
        .and()
        .areInterfaces()
        .and()
        .haveSimpleNameEndingWith("Repository")
        .should()
        .beAnnotatedWith(DddRepository::class.java)
        .because("永続化ポートは jMolecules の @Repository で役割を表明する（#737）")
