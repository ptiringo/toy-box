package com.example.api.architecture

import com.example.api.ApiApplication
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.library.GeneralCodingRules
import java.util.UUID

/**
 * 層に依らない一般的なコーディング規約を強制するテスト。
 *
 * 標準ストリームへの直接書き込み・フィールドインジェクション・`UUID.randomUUID()` の直接呼び出しを禁じる。 規約の全体像と意図は
 * `.claude/rules/architecture.md` を参照。
 */
@AnalyzeClasses(
    packagesOf = [ApiApplication::class],
    importOptions = [ImportOption.DoNotIncludeTests::class],
)
class GeneralCodingRulesTest {
    /** 標準出力・標準エラーへ直接書き込まないこと。 */
    @ArchTest val noStandardStreams = GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS

    /** フィールドインジェクションを使わずコンストラクタインジェクションを使うこと。 */
    @ArchTest val noFieldInjection = GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION

    /**
     * ID は `UUID.randomUUID()` を直接呼ばず `domain.shared.generateId()`（UUIDv7 相当のタイムベース生成）経由で生成すること。
     *
     * 生成値が時刻順にソート可能で永続化時のインデックス局所性に優れるため、全 ID をタイムベース生成に統一する（ADR-0005）。
     */
    @ArchTest
    val idsAreGeneratedViaGenerateId =
        noClasses()
            .should()
            .callMethod(UUID::class.java, "randomUUID")
            .because(
                "ID は domain.shared.generateId()（UUIDv7 相当のタイムベース生成）経由で生成する。" +
                    "永続化時のインデックス局所性のため UUID.randomUUID() の直接呼び出しは禁止（ADR-0005）"
            )
}
