package com.example.api.replay

import com.example.api.replay.fixture.FixtureLoader
import com.example.api.support.PostgresContainerSupport
import com.example.api.support.deleteAllStudbookTables
import java.nio.file.Path
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.context.TestConstructor
import org.springframework.test.context.TestConstructor.AutowireMode

/**
 * 実在馬の公開記録を繁殖ワークフローへ一周流し、停止点＝モデルの綻びを突合レポートに書き出す。
 *
 * **停止はテストの失敗ではなく「発見」**なので、停止そのものは assert しない （全フィクスチャを流す最後のテストがその契約を体現する）。例外は 2 つ。
 *
 * `01-imported-normal` は両親とも輸入馬＝事実どおりの seed 経路で入れられるため、一周完了を回帰アンカーとして固定する。
 * 分岐（馬名登録の有無・産駒登録の有無）を検証するテストは、「◯◯の段階が無いこと」が手前の停止でも 成立してしまう空振りを避けるため、判断地点への到達を前提条件として明示する。
 * ただしこの前提条件も `assert` にはしない。将来モデル側の綻びが直って分岐地点より手前で停止するように
 * なった場合、それ自体は「発見」でありビルド失敗にしてはならないため、`assumeTrue` で表現し、到達しなければ テストは失敗ではなく skipped として可視化する（内国産馬の
 * seed 経路の綻びは #633 で解消済み）。
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
        assert(
            outcome.steps
                .map { it.step }
                .containsAll(listOf(ReplayStep.REGISTER_FOAL, ReplayStep.NAME_FOAL))
        )
    }

    @Test
    fun `未命名の産駒では馬名登録の段階を実行しない`() {
        val outcome = engine.run(FixtureLoader.load("05-unnamed-foal.json"))

        // 「NAME_FOAL が無いこと」だけを見ると、その手前で停止した場合にも通ってしまう（空振り）。
        // 分岐地点（産駒血統登録）に到達していなければ「停止＝発見」なので失敗にせず skip する。
        assumeTrue(outcome.steps.any { it.step == ReplayStep.REGISTER_FOAL && it.ok }) {
            "馬名登録の分岐地点（REGISTER_FOAL）に到達しないまま停止: " +
                "stoppedAt=${outcome.stoppedAt} / ${outcome.stopReason} / steps=${outcome.steps}"
        }
        assert(outcome.steps.none { it.step == ReplayStep.NAME_FOAL })
    }

    @Test
    fun `産駒なしの年は産駒登録を経ずに繁殖成績報告まで到達する`() {
        val outcome = engine.run(FixtureLoader.load("02-domestic-barren.json"))

        // 同上。産駒登録が「無いこと」は、繁殖成績報告まで到達して初めて「スキップされた」と言える。
        // 分岐地点に到達していなければ「停止＝発見」なので失敗にせず skip する。
        assumeTrue(outcome.steps.any { it.step == ReplayStep.SUBMIT_BREEDING_REPORT && it.ok }) {
            "繁殖成績報告の分岐地点（SUBMIT_BREEDING_REPORT）に到達しないまま停止: " +
                "stoppedAt=${outcome.stoppedAt} / ${outcome.stopReason} / steps=${outcome.steps}"
        }
        assert(outcome.steps.none { it.step == ReplayStep.REGISTER_FOAL })
        assert(outcome.steps.none { it.step == ReplayStep.NAME_FOAL })
    }

    @Test
    fun `種付なしの年は種付記録を経ずに繁殖成績報告まで到達する`() {
        val outcome = engine.run(FixtureLoader.load("06-uncovered-season.json"))

        // 「種付の段階が無いこと」だけを見ると、その手前で停止した場合にも通ってしまう（空振り）。
        // 繁殖成績報告まで到達して初めて「種付を経ずに一周した」と言える。到達しなければ「停止＝発見」
        // なので失敗にせず skip する。
        assumeTrue(outcome.steps.any { it.step == ReplayStep.SUBMIT_BREEDING_REPORT && it.ok }) {
            "繁殖成績報告に到達しないまま停止: " +
                "stoppedAt=${outcome.stoppedAt} / ${outcome.stopReason} / steps=${outcome.steps}"
        }
        assert(outcome.steps.any { it.step == ReplayStep.RECORD_UNCOVERED && it.ok })
        // 種付が存在しない年なので、種付記録・種付成績報告・出生報告・産駒登録・馬名登録は実行しない。
        assert(
            outcome.steps.none {
                it.step in
                    listOf(
                        ReplayStep.REGISTER_STALLION,
                        ReplayStep.REGISTER_STALLION_BREEDING,
                        ReplayStep.RECORD_COVERING,
                        ReplayStep.SUBMIT_COVERING_REPORT,
                        ReplayStep.REPORT_FOALING,
                        ReplayStep.REGISTER_FOAL,
                        ReplayStep.NAME_FOAL,
                    )
            }
        )
    }

    @Test
    fun `全フィクスチャを流して突合レポートを書き出す`() {
        // 停止は「発見」なので失敗にしない。観測をレポートへ落とすことがこのテストの目的。
        val outcomes = FixtureLoader.loadAll().map(engine::run)

        assert(outcomes.isNotEmpty())
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
