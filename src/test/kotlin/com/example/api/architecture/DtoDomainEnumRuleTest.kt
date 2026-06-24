package com.example.api.architecture

import com.example.api.controller.archfixture.DomainEnumFieldDtoFixture
import com.example.api.domain.studbook.model.horse.bloodhorse.CoatColor
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * `ArchitectureTest.dtosDoNotExposeDomainEnums`（DTO がドメイン enum をフィールド型に晒さない規約）の
 * メタテスト。ルールが「違反を検出する」「マッパー関数を誤検出しない」の双方を能動的に検証する（#297 の完了条件）。
 *
 * 本番の `@ArchTest` は全クラスに対し現状違反ゼロを保証するが、ルールが**実際に噛む**ことは別途確かめないと
 * 「常に成功するだけの無力なルール」と区別できない。ここでは違反フィクスチャを与えて失敗することと、実 controller （ドメイン enum
 * を引数・戻り値で扱うマッパー関数を含む）に対しては成功することを確認する。
 */
class DtoDomainEnumRuleTest {
    private val rule = ArchitectureTest().dtosDoNotExposeDomainEnums

    @Test
    fun `フィールド型にドメインenumを持つDTOは違反として検出されること`() {
        // controller 配下でフィールド型にドメイン enum（CoatColor）を持つフィクスチャを与えると違反になる。
        val classes =
            ClassFileImporter()
                .importClasses(DomainEnumFieldDtoFixture::class.java, CoatColor::class.java)

        assertThrows<AssertionError> { rule.check(classes) }
    }

    @Test
    fun `実controllerのDTOとマッパー関数は違反しないこと`() {
        // 実 controller には `〜Dto` enum を使う DTO と、ドメイン enum を引数・戻り値で扱うマッパー関数
        // （BloodHorseWireEnums 等）が同居する。マッパーは「フィールド」ではないため誤検出されないことを確認する。
        val classes =
            ClassFileImporter()
                .withImportOption(ImportOption.DoNotIncludeTests())
                .importPackages("com.example.api.controller")

        rule.check(classes)
    }
}
