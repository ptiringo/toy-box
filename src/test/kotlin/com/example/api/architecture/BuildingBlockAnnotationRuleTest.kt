package com.example.api.architecture

import com.example.api.architecture.fixture.UnannotatedAggregateFixture
import com.example.api.architecture.fixture.UnannotatedFixtureRepository
import com.example.api.architecture.fixture.UnannotatedValueClassFixture
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * jMolecules アノテーションの付け忘れを検出するルール群（#737）のメタテスト。
 *
 * 各ルールが「付け忘れたフィクスチャを違反として検出する」「実ドメインは違反しない」の双方を検証する。`classes().should()` は 対象が 0
 * 件でもパスするため、検出側を確かめないと「常に成功するだけの無力なルール」と区別できない。
 */
class BuildingBlockAnnotationRuleTest {
    private val rules = DomainModelingRulesTest()

    private val fixturePackage = "${UnannotatedAggregateFixture::class.java.packageName}.."

    private val realDomain =
        ClassFileImporter()
            .withImportOption(ImportOption.DoNotIncludeTests())
            .importPackages("com.example.api.domain")

    @Test
    fun `Entity を継承しているのに役割アノテーションの無い型は違反として検出されること`() {
        val classes = ClassFileImporter().importClasses(UnannotatedAggregateFixture::class.java)

        assertThrows<AssertionError> {
            rules.entitySubtypesAreAnnotatedAsAggregateOrEntity.check(classes)
        }
    }

    @Test
    fun `ValueObject の無い inline value class は違反として検出されること`() {
        val classes = ClassFileImporter().importClasses(UnannotatedValueClassFixture::class.java)

        assertThrows<AssertionError> {
            inlineValueClassesAreValueObjects(fixturePackage).check(classes)
        }
    }

    @Test
    fun `Repository の無い Repository 命名の interface は違反として検出されること`() {
        val classes = ClassFileImporter().importClasses(UnannotatedFixtureRepository::class.java)

        assertThrows<AssertionError> { repositoryPortsAreAnnotated(fixturePackage).check(classes) }
    }

    @Test
    fun `実ドメインの集約・値オブジェクト・リポジトリポートは違反しないこと`() {
        rules.entitySubtypesAreAnnotatedAsAggregateOrEntity.check(realDomain)
        rules.inlineValueClassesAreValueObjects.check(realDomain)
        rules.repositoryPortsAreAnnotated.check(realDomain)
    }
}
