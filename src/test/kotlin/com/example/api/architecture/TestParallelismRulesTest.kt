package com.example.api.architecture

import com.example.api.architecture.fixture.ConcurrentWebMvcTestFixture
import com.example.api.architecture.fixture.ParallelUnsafeWebMvcTestFixture
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest

/**
 * テストコード自身に掛ける規約（#690 / ADR-0079）。
 *
 * テストはクラス間並列で走る（`build.gradle.kts` の `junit.jupiter.execution.parallel.*`）が、**並列にしてはいけない テストが 2
 * 種類ある**。DB を触るテスト（全テーブル TRUNCATE が並行実行と両立しない。ADR-0070）と `@WebMvcTest` スライス（`@MockkBean` ＝
 * `@MockBean` 機構が Spring 公式「Parallel Test Execution」の非推奨条件に該当する）。
 *
 * 前者は `PostgresContainerSupport` のクラス注釈が `@Inherited` で継承先すべてに効くので、継承しさえすれば守られる。
 * 後者は**クラスごとに書くしかなく付け忘れが起きうる**。しかも付け忘れは例外にならず、非推奨条件のまま静かに走って 不安定さとして後から出るため、ここで機械強制する。
 *
 * 他の `〜RulesTest` と違い `@AnalyzeClasses` を使わないのは、それが `ImportOption.DoNotIncludeTests` で
 * **テストコードを走査対象から外している**ため。本ルールの対象はテストコードそのものなので、`ClassFileImporter` で 明示的に読み込む。
 */
class TestParallelismRulesTest {
    /**
     * `@WebMvcTest` を付けたクラスは `@Execution(SAME_THREAD)` で並列対象から外す。
     *
     * `@Execution` の有無だけでなく**値が `SAME_THREAD` であること**まで見る。`CONCURRENT` を明示した場合も
     * 並列で走ってしまい、付け忘れと同じ結果になるため。
     */
    val webMvcTestsRunInSameThread: ArchRule =
        classes()
            .that()
            .areAnnotatedWith(WebMvcTest::class.java)
            .should(runInSameThread())
            .because("@WebMvcTest は @MockkBean を使うため、Spring 公式が JVM 内並列の非推奨条件としている（ADR-0079）")

    @Test
    fun `すべての @WebMvcTest が SAME_THREAD で走ること`() {
        // fixture は「違反サンプル」なので走査から外す（除外し忘れるとゲートが常に赤くなる）。
        val classes =
            ClassFileImporter()
                .withImportOption { location -> !location.contains(FIXTURE_PATH) }
                .importPackages(ROOT_PACKAGE)

        webMvcTestsRunInSameThread.check(classes)
    }

    @Test
    fun `@Execution が無い @WebMvcTest は違反として検出されること`() {
        val classes = ClassFileImporter().importClasses(ParallelUnsafeWebMvcTestFixture::class.java)

        assertThrows<AssertionError> { webMvcTestsRunInSameThread.check(classes) }
    }

    @Test
    fun `@Execution(CONCURRENT) を明示した @WebMvcTest も違反として検出されること`() {
        // 付け忘れと同じく並列で走ってしまうため、注釈の有無ではなく値まで見ていることを担保する。
        val classes = ClassFileImporter().importClasses(ConcurrentWebMvcTestFixture::class.java)

        assertThrows<AssertionError> { webMvcTestsRunInSameThread.check(classes) }
    }

    @Test
    fun `走査対象に @WebMvcTest が 1 つも無いと空振りするため、実際に拾えていることを確かめる`() {
        // noClasses/classes の should は対象 0 件でも成功するため、母集団が空でないことを別途押さえる
        // （.claude/rules/gates.md「空振りしているゲートは何もしていない」）。
        val classes =
            ClassFileImporter()
                .withImportOption { location -> !location.contains(FIXTURE_PATH) }
                .importPackages(ROOT_PACKAGE)
        val webMvcTests = classes.filter { it.isAnnotatedWith(WebMvcTest::class.java) }

        assert(webMvcTests.count() > 0) { "@WebMvcTest が 1 つも走査されていない（走査範囲の誤り）" }
    }

    private companion object {
        const val ROOT_PACKAGE = "com.example.api"
        const val FIXTURE_PATH = "/architecture/fixture/"

        fun runInSameThread(): ArchCondition<JavaClass> =
            object : ArchCondition<JavaClass>("@Execution(SAME_THREAD) が付いている") {
                override fun check(item: JavaClass, events: ConditionEvents) {
                    val execution = item.tryGetAnnotationOfType(Execution::class.java)
                    val satisfied =
                        execution.isPresent && execution.get().value == ExecutionMode.SAME_THREAD
                    val message =
                        "${item.name} に @Execution(ExecutionMode.SAME_THREAD) が付いていない" +
                            "（現在: ${execution.map { it.value.name }.orElse("注釈なし")}）"
                    events.add(SimpleConditionEvent(item, satisfied, message))
                }
            }
    }
}
