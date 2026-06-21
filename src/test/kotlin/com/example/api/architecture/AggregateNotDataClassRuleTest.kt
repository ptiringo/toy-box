package com.example.api.architecture

import com.example.api.architecture.fixture.DataClassAggregateFixture
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * `ArchitectureTest.aggregatesAreNotDataClasses`（集約に data class を使わない規約）のメタテスト。
 *
 * ルールが「data class の集約を検出する」「private ctor ＋手書き copy を持つ実集約は誤検出しない」の双方を能動的に 検証する（#307 の完了条件）。本番の
 * `@ArchTest` は現状違反ゼロを保証するが、ルールが実際に噛むことは別途確かめないと 「常に成功するだけの無力なルール」と区別できない。
 */
class AggregateNotDataClassRuleTest {
    private val rule = ArchitectureTest().aggregatesAreNotDataClasses

    @Test
    fun `data class の集約は違反として検出されること`() {
        // @AggregateRoot data class（componentN を生成する）を与えると違反になる。
        val classes = ClassFileImporter().importClasses(DataClassAggregateFixture::class.java)

        assertThrows<AssertionError> { rule.check(classes) }
    }

    @Test
    fun `private ctor と手書き copy を持つ実集約は違反しないこと`() {
        // 実ドメインの集約（BloodHorse 等）は private ctor ＋手書き copy で書かれ componentN を持たない。
        val classes =
            ClassFileImporter()
                .withImportOption(ImportOption.DoNotIncludeTests())
                .importPackages("com.example.api.domain")

        rule.check(classes)
    }
}
