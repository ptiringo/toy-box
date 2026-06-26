package com.example.api.architecture

import com.example.api.ApiApplication
import com.tngtech.archunit.base.DescribedPredicate.not
import com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

/**
 * HTTP アダプター（controller 層）の契約に関する規約を強制するテスト。
 *
 * `@RestController` の配置・成功レスポンスの返し方・DTO がドメイン enum を晒さないこと・request/problem サブパッケージの 構成 allowlist
 * を検証する。規約の全体像と意図は `.claude/rules/architecture.md` / `.claude/rules/api-design.md`、メタテストは
 * [DtoDomainEnumRuleTest] / [ControllerPackageLayoutRuleTest] を参照。
 */
@AnalyzeClasses(
    packagesOf = [ApiApplication::class],
    importOptions = [ImportOption.DoNotIncludeTests::class],
)
class ControllerContractRulesTest {
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
     * 成功レスポンスは `@ResponseStatus` ＋戻り値で resource を返す(`.claude/rules/error-handling.md` /
     * `.claude/rules/api-design.md`)。エラー描画 funnel の `GlobalExceptionHandler` は `@RestController`
     * ではない(`ResponseEntityExceptionHandler` 継承)ため、本ルールでは誤検出されない。
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

    /**
     * リクエスト DTO（`〜Request`）は各リソースの `request/` サブパッケージに置くこと。
     *
     * 1 リソースに操作が増えるほど Request DTO が増えるため、resource パッケージ直下から `request/` へ隔離して
     * 肥大化を防ぐ（`.claude/rules/api-design.md`「パッケージ構成」/ ADR-0028）。Controller・単一リソース表現 （`〜Response`）・共有
     * wire enum は resource 直下に残す。ルールが実際に違反を検出することは `ControllerPackageLayoutRuleTest` で別途担保する。
     */
    @ArchTest
    val requestDtosResideInRequestSubpackage =
        classes()
            .that()
            .haveSimpleNameEndingWith("Request")
            .and()
            .resideInAPackage(CONTROLLER)
            .should()
            .resideInAPackage("..request..")
            .because("リクエスト DTO は各リソースの request/ サブパッケージへ隔離する（ADR-0028）")

    /**
     * Problem 変換（`〜Problem.kt` の `toProblemDetail()` 拡張関数群）は各リソースの `problem/` サブパッケージに置くこと。
     *
     * 拡張関数はファイルごとのファサードクラス（`〜ProblemKt`）へコンパイルされるため、`〜ProblemKt` が `problem/`
     * 配下に居ることを検証する（`.claude/rules/api-design.md`「パッケージ構成」/ ADR-0028）。エラー描画ヘルパ
     * （`ProblemResponses.kt` ＝ `ProblemResponsesKt`）は `Problem` で終わらないため誤検出されない。ルールが実際に 違反を検出することは
     * `ControllerPackageLayoutRuleTest` で別途担保する。
     */
    @ArchTest
    val problemMappersResideInProblemSubpackage =
        classes()
            .that()
            .haveSimpleNameEndingWith("ProblemKt")
            .and()
            .resideInAPackage(CONTROLLER)
            .should()
            .resideInAPackage("..problem..")
            .because("Problem 変換は各リソースの problem/ サブパッケージへ集約する（ADR-0028）")

    /**
     * `request/` サブパッケージには Request DTO（と入力マッピング facade）のみ置くこと（逆方向の allowlist）。
     *
     * `requestDtosResideInRequestSubpackage`（順方向）と対にして iff を成し、`request/` に無関係なクラスが
     * 紛れ込む抜け穴を塞ぐ。トップレベルの入力マッピング（`toCommand()` 等）はファイル facade `〜RequestKt` へ
     * コンパイルされるため、`〜Request`（data class）または `〜RequestKt`（facade）のいずれかであることを要求する。 ルールが実際に違反を検出することは
     * `ControllerPackageLayoutRuleTest` で別途担保する。
     */
    @ArchTest
    val requestSubpackageContainsOnlyRequests =
        classes()
            .that()
            .resideInAPackage("..controller..request..")
            .and(not(isSyntheticClass))
            .should()
            .haveSimpleNameEndingWith("Request")
            .orShould()
            .haveSimpleNameEndingWith("RequestKt")
            .because("request/ には 〜Request DTO と入力マッピング facade 〜RequestKt のみ置く（ADR-0028）")

    /**
     * `problem/` サブパッケージには Problem 変換 facade（`〜ProblemKt`）のみ置くこと（逆方向の allowlist）。
     *
     * `problemMappersResideInProblemSubpackage`（順方向）と対にして iff を成す。Problem 変換は拡張関数群であり ファイル facade
     * `〜ProblemKt` へコンパイルされるため、`problem/` 配下のクラスは `〜ProblemKt` に限る。 ルールが実際に違反を検出することは
     * `ControllerPackageLayoutRuleTest` で別途担保する。
     */
    @ArchTest
    val problemSubpackageContainsOnlyProblemMappers =
        classes()
            .that()
            .resideInAPackage("..controller..problem..")
            .and(not(isSyntheticClass))
            .should()
            .haveSimpleNameEndingWith("ProblemKt")
            .because("problem/ には Problem 変換 facade 〜ProblemKt のみ置く（ADR-0028）")
}
