package com.example.api.architecture

import com.example.api.ApiApplication
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices

/**
 * 境界づけられたコンテキスト間の分離を強制するテスト。
 *
 * application / domain / infrastructure 各層の直下のパッケージ名（studbook / racing / sakamichi / tennis）を
 * コンテキストとみなし、コンテキスト間の依存を禁じる。スライス割り当ては [BoundedContextAssignment]、 規約の全体像と意図は
 * `.claude/rules/architecture.md` を参照。
 */
@AnalyzeClasses(
    packagesOf = [ApiApplication::class],
    importOptions = [ImportOption.DoNotIncludeTests::class],
)
class BoundedContextRulesTest {
    /** 境界づけられたコンテキスト同士は依存しないこと。 */
    @ArchTest
    val boundedContextsAreIsolated =
        slices().assignedFrom(BoundedContextAssignment).should().notDependOnEachOther()
}
