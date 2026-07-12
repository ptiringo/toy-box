package com.example.api.replay

import com.example.api.replay.fixture.FixtureLoader
import com.example.api.support.PostgresContainerSupport
import com.example.api.support.deleteAllStudbookTables
import java.nio.file.Path
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.context.TestConstructor
import org.springframework.test.context.TestConstructor.AutowireMode

/**
 * 実在馬の公開記録を繁殖ワークフローへ一周流し、停止点＝モデルの綻びを突合レポートに書き出す。
 *
 * **停止はテストの失敗ではなく「発見」**なので assert しない。唯一の例外が `01-imported-normal` で、 両親とも輸入馬＝事実どおりの seed
 * 経路で入れられるため、一周完了を回帰アンカーとして固定する。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestConstructor(autowireMode = AutowireMode.ALL)
class BreedingReplayTest(private val engine: ReplayEngine, private val jdbcClient: JdbcClient) :
    PostgresContainerSupport() {

    @BeforeEach fun cleanUp() = deleteAllStudbookTables(jdbcClient)

    @Test
    fun `輸入牝馬と輸入種牡馬の正常系は繁殖サイクルを最後まで一周する`() {
        val outcome = engine.run(FixtureLoader.load("01-imported-normal.json"))

        assert(outcome.stoppedAt == null) {
            "想定外の停止: ${outcome.stoppedAt} / ${outcome.stopReason} / steps=${outcome.steps}"
        }
        assert(outcome.steps.all { it.ok })
        assert(outcome.steps.map { it.step }.contains(ReplayStep.SUBMIT_BREEDING_REPORT))
    }

    @Test
    fun `未命名の産駒では馬名登録の段階を実行しない`() {
        val outcome = engine.run(FixtureLoader.load("05-unnamed-foal.json"))

        assert(outcome.steps.none { it.step == ReplayStep.NAME_FOAL })
    }

    @Test
    fun `産駒なしの年は産駒登録を経ずに繁殖成績報告まで到達する`() {
        val outcome = engine.run(FixtureLoader.load("02-domestic-barren.json"))

        assert(outcome.steps.none { it.step == ReplayStep.REGISTER_FOAL })
        assert(outcome.steps.none { it.step == ReplayStep.NAME_FOAL })
    }

    @Test
    fun `全フィクスチャを流して突合レポートを書き出す`() {
        // 停止は「発見」なので失敗にしない。観測をレポートへ落とすことがこのテストの目的。
        val outcomes = FixtureLoader.loadAll().map(engine::run)

        assert(outcomes.size == 5)
        val report = ReconciliationReport.render(outcomes)
        assert(report.contains("# 繁殖 replay 突合レポート"))
        assert(report.contains("## 公開記録の粒度について"))
        assert(report.contains("### 合成した項目"))
        // 合成の理由が 1 頭も書かれていないなら、2 層スキーマが機能していない。
        assert(outcomes.all { it.synthesizedNotes.isNotEmpty() })
        ReconciliationReport.write(
            outcomes,
            Path.of("build", "reports", "replay", "reconciliation.md"),
        )
    }
}
