package com.example.api.replay

import org.junit.jupiter.api.Test

/**
 * 突合レポートの描画を検証する。DB を要さない（[ReconciliationReport.render] は純粋関数）。
 *
 * 停止した観測は実在馬からは生まれない（実在馬 6 頭はいずれも一周完走する）ため、ここでは観測を直接組み立てる。 停止を実際に発生させる検証は [ReplayStopBranchTest]
 * が担う。
 */
class ReconciliationReportTest {
    private val stopped =
        HorseReplayOutcome(
            fixtureName = "テスト用・停止する観測",
            sources = listOf(SourceRef("繁殖牝馬", "https://example.invalid/broodmare")),
            synthesizedNotes = listOf("種付日は非公開のため合成した。"),
            steps =
                listOf(
                    StepResult(ReplayStep.REGISTER_STALLION, true, "ok"),
                    StepResult(
                        ReplayStep.RECORD_COVERING,
                        false,
                        "InvalidCovering(cause=OutsideValidRegion(coveringPlace=北海道, " +
                            "validRegions=[青森]))",
                    ),
                ),
            stoppedAt = ReplayStep.RECORD_COVERING,
            stopReason =
                "InvalidCovering(cause=OutsideValidRegion(coveringPlace=北海道, validRegions=[青森]))",
        )

    private val completed =
        HorseReplayOutcome(
            fixtureName = "テスト用・一周完了する観測",
            sources = listOf(SourceRef("繁殖牝馬", "https://example.invalid/broodmare")),
            synthesizedNotes = listOf("マイクロチップ番号は非公開のため合成した。"),
            steps = listOf(StepResult(ReplayStep.SUBMIT_BREEDING_REPORT, true, "ok")),
            stoppedAt = null,
            stopReason = null,
        )

    @Test
    fun `停止した観測は停止段階と弾いた不変条件を描画する`() {
        val report = ReconciliationReport.render(listOf(stopped))

        assert(report.contains("- 結果: ⛔ RECORD_COVERING で停止"))
        assert(report.contains("- 弾いた不変条件/エラー: `${stopped.stopReason}`"))
        assert(!report.contains("✅ 一周完了"))
    }

    @Test
    fun `停止した観測でも合成した項目と実行した段階を描画する`() {
        val report = ReconciliationReport.render(listOf(stopped))

        assert(report.contains("### 合成した項目・断り書き"))
        assert(report.contains("- 種付日は非公開のため合成した。"))
        assert(report.contains("### 実行した段階"))
        assert(report.contains("| RECORD_COVERING | NG |"))
        assert(report.contains("| REGISTER_STALLION | ok |"))
    }

    @Test
    fun `一周完了した観測には停止の行を描画しない`() {
        val report = ReconciliationReport.render(listOf(completed))

        assert(report.contains("- 結果: ✅ 一周完了"))
        assert(!report.contains("⛔"))
        assert(!report.contains("弾いた不変条件/エラー"))
    }

    @Test
    fun `サマリは停止と一周完了の頭数を数える`() {
        val report = ReconciliationReport.render(listOf(stopped, completed))

        assert(report.contains("- 対象: 2 頭"))
        assert(report.contains("- 一周完了: 1 頭"))
        assert(report.contains("- 停止（モデルが弾いた）: 1 頭"))
    }
}
