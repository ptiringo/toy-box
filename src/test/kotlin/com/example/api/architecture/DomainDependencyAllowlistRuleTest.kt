package com.example.api.architecture

import com.example.api.architecture.fixture.LoggerDependentDomainFixture
import com.example.api.architecture.fixture.PureDomainFixture
import com.example.api.architecture.fixture.SpringDependentDomainFixture
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * domain 層の依存許可リスト（`OnionLayerRulesTest.domainIsFrameworkFree`、#784）のメタテスト。
 *
 * 旧 denylist が素通りさせていた依存（slf4j）と、旧 denylist でも捕まえていた依存（Spring）の双方を検出し、
 * 許可リスト内の依存と実ドメインは違反しないことを確かめる。
 */
class DomainDependencyAllowlistRuleTest {
    private val fixturePackage = "${PureDomainFixture::class.java.packageName}.."

    private fun fixture(type: Class<*>) = ClassFileImporter().importClasses(type)

    @Test
    fun `旧 denylist の列挙外だったライブラリへの依存を違反として検出すること`() {
        assertThrows<AssertionError> {
            domainDependsOnlyOnAllowedLibraries(fixturePackage)
                .check(fixture(LoggerDependentDomainFixture::class.java))
        }
    }

    @Test
    fun `Spring への依存を違反として検出すること`() {
        assertThrows<AssertionError> {
            domainDependsOnlyOnAllowedLibraries(fixturePackage)
                .check(fixture(SpringDependentDomainFixture::class.java))
        }
    }

    @Test
    fun `許可リストのライブラリだけに依存する型は違反しないこと`() {
        domainDependsOnlyOnAllowedLibraries(fixturePackage)
            .check(fixture(PureDomainFixture::class.java))
    }

    @Test
    fun `実ドメインは違反しないこと`() {
        val domain =
            ClassFileImporter()
                .withImportOption(ImportOption.DoNotIncludeTests())
                .importPackages("com.example.api.domain")

        OnionLayerRulesTest().domainIsFrameworkFree.check(domain)
    }
}
