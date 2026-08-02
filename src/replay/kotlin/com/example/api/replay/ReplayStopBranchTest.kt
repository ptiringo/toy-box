package com.example.api.replay

import com.example.api.replay.fixture.CoveredSeasonFixture
import com.example.api.replay.fixture.FixtureLoader
import com.example.api.replay.fixture.StudCertificateFixture
import com.example.api.support.PostgresContainerSupport
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestConstructor
import org.springframework.test.context.TestConstructor.AutowireMode

/**
 * モデルが不正な種付を弾いて停止することを検証する。
 *
 * ここで流すのは**実在馬ではない**。一周完走する回帰アンカー `01-imported-normal`（種付日 2001-04-15・種付場所 北海道・ 種畜証明の有効区域
 * [北海道]・有効期間 2001-02-01〜07-31）の種畜証明だけを 1 点崩した反実仮想であり、
 * 事実ではないので突合レポート（`fixtures/manifest.txt`）には載せない。載せると、レポートの ⛔ が
 * 「モデルが実在の事実を弾いた（＝発見）」なのか「不正データを想定どおり拒否した（＝正常）」なのか区別できなくなる。
 *
 * したがって [BreedingReplayTest] の「停止は失敗ではなく発見なので assert しない」契約は**ここには適用しない**。
 * 崩した以上は停止しなければならず、停止しなければ検証（種畜証明の有効区域・有効期間）が効いていないということなので失敗させる。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestConstructor(autowireMode = AutowireMode.ALL)
class ReplayStopBranchTest(private val engine: ReplayEngine) : PostgresContainerSupport() {

    /** 回帰アンカーの種畜証明だけを差し替える（他の値はすべて元のまま）。 */
    private fun anchorWithStudCertificate(
        certificate: (StudCertificateFixture) -> StudCertificateFixture
    ): CoveredSeasonFixture {
        val anchor = FixtureLoader.load("01-imported-normal.json") as CoveredSeasonFixture
        val covering = anchor.synthesized.covering
        return anchor.copy(
            synthesized =
                anchor.synthesized.copy(
                    covering =
                        covering.copy(studCertificate = certificate(covering.studCertificate))
                )
        )
    }

    @Test
    fun `種付場所が種畜証明の有効区域外なら種付の記録で停止する`() {
        // 種付場所は 北海道 のまま、有効区域だけを 青森 に差し替える。
        val fixture = anchorWithStudCertificate { it.copy(validRegions = listOf("青森")) }

        val outcome = engine.run(fixture)

        assert(outcome.stoppedAt == ReplayStep.RECORD_COVERING) {
            "停止しなかった: stoppedAt=${outcome.stoppedAt} / steps=${outcome.steps}"
        }
        assert(outcome.stopReason?.contains("OutsideValidRegion") == true) {
            "弾いた不変条件が有効区域ではない: ${outcome.stopReason}"
        }
        assertStoppedAtRecordCovering(outcome)
    }

    @Test
    fun `種付日が種畜証明の有効期間外なら種付の記録で停止する`() {
        // 種付日は 2001-04-15 のまま、有効期間の開始を 2001-05-01 にずらして種付日を期間の外に出す。
        val fixture = anchorWithStudCertificate {
            it.copy(validPeriodStart = "2001-05-01", validPeriodEnd = "2001-07-31")
        }

        val outcome = engine.run(fixture)

        assert(outcome.stoppedAt == ReplayStep.RECORD_COVERING) {
            "停止しなかった: stoppedAt=${outcome.stoppedAt} / steps=${outcome.steps}"
        }
        assert(outcome.stopReason?.contains("OutsideValidPeriod") == true) {
            "弾いた不変条件が有効期間ではない: ${outcome.stopReason}"
        }
        assertStoppedAtRecordCovering(outcome)
    }

    /** 本当に RECORD_COVERING で止まっている（手前は通り、以降は 1 段階も走っていない）ことを確かめる。 */
    private fun assertStoppedAtRecordCovering(outcome: HorseReplayOutcome) {
        val steps = outcome.steps.map { it.step }
        assert(steps.last() == ReplayStep.RECORD_COVERING)
        assert(!outcome.steps.last().ok)
        assert(outcome.steps.dropLast(1).all { it.ok })
        assert(
            steps.none {
                it in
                    listOf(
                        ReplayStep.SUBMIT_COVERING_REPORT,
                        ReplayStep.REPORT_FOALING,
                        ReplayStep.REGISTER_FOAL,
                        ReplayStep.NAME_FOAL,
                        ReplayStep.SUBMIT_BREEDING_REPORT,
                    )
            }
        )
    }
}
