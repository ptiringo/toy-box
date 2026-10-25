package com.example.api.architecture

import com.example.api.architecture.fixture.ActorConstructingToolFixture
import com.example.api.architecture.fixture.ActorCopyingToolFixture
import com.example.api.architecture.fixture.QueriesInjectingToolFixture
import com.example.api.architecture.fixture.RepositoryInjectingToolFixture
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * MCP アダプタの迂回を検出するルール群（#752）のメタテスト。
 *
 * 各ルールが「迂回するフィクスチャを違反として検出する」「実際の MCP アダプタは違反しない」の双方を検証する。 `noClasses().should()` はマッチが 0
 * 件でもパスするため、検出側を確かめないと空振りと区別できない。
 */
class McpAdapterRuleTest {
    private val rules = McpAdapterRulesTest()

    private val fixturePackage = "${ActorConstructingToolFixture::class.java.packageName}.."

    private fun fixture(type: Class<*>) = ClassFileImporter().importClasses(type)

    @Test
    fun `Actor を直接組むツールは違反として検出されること`() {
        assertThrows<AssertionError> {
            actorIsBuiltOnlyByFactory(fixturePackage)
                .check(fixture(ActorConstructingToolFixture::class.java))
        }
    }

    @Test
    fun `Actor を copy で組み替えるツールは違反として検出されること`() {
        assertThrows<AssertionError> {
            actorIsBuiltOnlyByFactory(fixturePackage)
                .check(fixture(ActorCopyingToolFixture::class.java))
        }
    }

    @Test
    fun `Queries ポートを直接注入するツールは違反として検出されること`() {
        assertThrows<AssertionError> {
            toolsDoNotDependOnPersistencePorts(fixturePackage)
                .check(fixture(QueriesInjectingToolFixture::class.java))
        }
    }

    @Test
    fun `Repository ポートを直接注入するツールは違反として検出されること`() {
        assertThrows<AssertionError> {
            toolsDoNotDependOnPersistencePorts(fixturePackage)
                .check(fixture(RepositoryInjectingToolFixture::class.java))
        }
    }

    @Test
    fun `実際の MCP アダプタは違反しないこと`() {
        // McpActorFactory 自身は Actor を組み、WorldQueries / AccountRepository に依存するが除外されている。
        val mcp =
            ClassFileImporter()
                .withImportOption(ImportOption.DoNotIncludeTests())
                .importPackages("com.example.api.mcp")

        rules.actorIsBuiltOnlyByFactory.check(mcp)
        rules.toolsDoNotDependOnPersistencePorts.check(mcp)
    }
}
