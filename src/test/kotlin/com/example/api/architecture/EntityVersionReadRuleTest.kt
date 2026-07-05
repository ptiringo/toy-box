package com.example.api.architecture

import com.example.api.architecture.fixture.SelfCopyAggregateFixture
import com.example.api.architecture.fixture.VersionBranchingFixture
import com.example.api.architecture.fixture.VersionedAggregateFixture
import com.example.api.domain.shared.Entity
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * `readsEntityVersionOfAnotherClass` 述語（version 業務判断禁止ルールの中核）のメタテスト。
 *
 * Kotlin のプロパティ読み取りが getter 呼び出し（`getVersion()`）としてバイトコードに現れることに依存した 述語なので、「違反を実際に検出する」「集約自身の
 * `copy` の自己参照は検出しない」の双方を fixture で能動的に 検証する（ArchUnit×Kotlin
 * の罠による無音の空振りを防ぐ。[AggregateNotDataClassRuleTest] と同じ流儀）。 本番ルール（パッケージスコープ付き）は fixture
 * パッケージに噛まないため、述語を無スコープの ルールに載せて検証する。
 */
class EntityVersionReadRuleTest {
    private val ruleOnAnyClass =
        noClasses().should().callMethodWhere(readsEntityVersionOfAnotherClass)

    @Test
    fun `他クラスの version を読む業務分岐は違反として検出されること`() {
        val classes =
            ClassFileImporter()
                .importClasses(
                    VersionBranchingFixture::class.java,
                    VersionedAggregateFixture::class.java,
                    Entity::class.java,
                )

        assertThrows<AssertionError> { ruleOnAnyClass.check(classes) }
    }

    @Test
    fun `自クラスの version を copy で引き回す自己参照は違反しないこと`() {
        val classes =
            ClassFileImporter()
                .importClasses(SelfCopyAggregateFixture::class.java, Entity::class.java)

        ruleOnAnyClass.check(classes)
    }
}
