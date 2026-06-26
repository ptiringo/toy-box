package com.example.api.architecture

import com.example.api.ApiApplication
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields
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
 * `.claude/rules/architecture.md` を参照。メタテストは [AggregateNotDataClassRuleTest] にある。
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
     * DDD ビルディングブロック（jMolecules アノテーション付きクラス）はドメインモデルリングに置くこと。
     *
     * 集約ルート / エンティティ / 値オブジェクト / リポジトリポートに加え、ドメインイベント（`@DomainEvent`）も
     * 対象に含める。イベントもドメインモデルの一員であり、フレームワーク非依存の domain.*.model に置く（ADR-0029）。
     */
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
            .or()
            .areAnnotatedWith(DomainEvent::class.java)
            .should()
            .resideInAPackage(DOMAIN_MODEL)

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
}
