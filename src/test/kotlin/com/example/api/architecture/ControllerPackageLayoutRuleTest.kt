package com.example.api.architecture

import com.example.api.controller.archfixture.MisplacedRequest
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * `ArchitectureTest` の controller パッケージ構成ルールのメタテスト。順方向（`〜Request` は `request/` へ、 `〜ProblemKt` は
 * `problem/` へ）と逆方向 allowlist（`request/` / `problem/` には所定のものしか置けない）の 計 4 ルールが「違反を検出する」「実
 * controller を誤検出しない」の双方を能動的に検証する（ADR-0028）。
 *
 * 本番の `@ArchTest` は全クラスに対し現状違反ゼロを保証するが、ルールが**実際に噛む**ことは別途確かめないと 「常に成功するだけの無力なルール」と区別できない。とくに
 * Problem 系ルールは Kotlin の facade クラス （`〜ProblemKt`）の名前に依存するため、違反フィクスチャで確実に検出することを確かめる。
 */
class ControllerPackageLayoutRuleTest {
    private val requestRule = ArchitectureTest().requestDtosResideInRequestSubpackage
    private val problemRule = ArchitectureTest().problemMappersResideInProblemSubpackage
    private val requestAllowlistRule = ArchitectureTest().requestSubpackageContainsOnlyRequests
    private val problemAllowlistRule =
        ArchitectureTest().problemSubpackageContainsOnlyProblemMappers

    @Test
    fun `request サブパッケージ外の Request DTO は違反として検出されること`() {
        // controller 配下だが request/ の外に置かれた `〜Request` を与えると違反になる。
        val classes = ClassFileImporter().importClasses(MisplacedRequest::class.java)

        assertThrows<AssertionError> { requestRule.check(classes) }
    }

    @Test
    fun `problem サブパッケージ外の Problem マッパー facade は違反として検出されること`() {
        // facade クラス（MisplacedProblemKt）は Kotlin から `::class` で参照できないため、フィクスチャの
        // パッケージごと取り込んで判定する。`〜ProblemKt` が problem/ の外にあると違反になる。
        val classes = ClassFileImporter().importPackages("com.example.api.controller.archfixture")

        assertThrows<AssertionError> { problemRule.check(classes) }
    }

    @Test
    fun `request サブパッケージに 〜Request でないクラスがあると違反として検出されること`() {
        // request/ を含むパッケージに `〜Request` / `〜RequestKt` でないクラスを置くと allowlist 違反になる。
        val classes =
            ClassFileImporter().importPackages("com.example.api.controller.archfixture.request")

        assertThrows<AssertionError> { requestAllowlistRule.check(classes) }
    }

    @Test
    fun `problem サブパッケージに 〜ProblemKt でないクラスがあると違反として検出されること`() {
        // problem/ を含むパッケージに `〜ProblemKt` でないクラスを置くと allowlist 違反になる。
        val classes =
            ClassFileImporter().importPackages("com.example.api.controller.archfixture.problem")

        assertThrows<AssertionError> { problemAllowlistRule.check(classes) }
    }

    @Test
    fun `実controllerのRequestDTOとProblemマッパーは違反しないこと`() {
        // 実 controller では Request DTO（と入力マッピング facade）が request/ に、Problem 変換 facade が
        // problem/ に置かれており、順方向・逆方向のいずれのルールも満たす。
        val classes =
            ClassFileImporter()
                .withImportOption(ImportOption.DoNotIncludeTests())
                .importPackages("com.example.api.controller")

        requestRule.check(classes)
        problemRule.check(classes)
        requestAllowlistRule.check(classes)
        problemAllowlistRule.check(classes)
    }
}
