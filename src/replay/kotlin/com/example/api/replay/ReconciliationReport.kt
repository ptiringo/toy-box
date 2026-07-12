package com.example.api.replay

import java.nio.file.Files
import java.nio.file.Path

/** replay 観測を Markdown の突合レポートに描画・書き出す。停止＝モデリングのバックログ。 */
object ReconciliationReport {
    /**
     * 公開記録（JBIS-Search）の粒度に由来する、レポート全体にかかる但し書き。
     *
     * 停止が 0 件でも「モデルが事実に耐えた」とは言えない。非公開項目を合成で埋めて通しているうえ、 記録側の粒度がモデルより粗く、区分の選択自体が合成の判断になっているため。
     */
    private val RECORD_GRANULARITY_NOTES =
        listOf(
            "JBIS-Search は不受胎・流産・死産を区別せず、すべて『産駒なし』に丸める（公式 FAQ に明記）。" +
                "したがって LiveFoal 以外の FoalingOutcome の区分は、事実ではなくフィクスチャ側の合成の判断である。",
            "JBIS-Search の繁殖成績の年軸は産駒の生年であり、種付年ではない。" + "各フィクスチャの種付年は生年から 1 を引いて求めた近似値である。",
            "マイクロチップ番号・血統登録番号・繁殖登録番号・DNA 型判定・種付日・種付証明書・報告の提出日は" +
                "いずれも非公開のため合成している。馬ごとの合成内容は各節の「合成した項目」を参照。",
        )

    fun render(outcomes: List<HorseReplayOutcome>): String {
        val sb = StringBuilder()
        sb.appendLine("# 繁殖 replay 突合レポート")
        sb.appendLine()
        val stopped = outcomes.filter { it.stoppedAt != null }
        sb.appendLine("- 対象: ${outcomes.size} 頭")
        sb.appendLine("- 一周完了: ${outcomes.size - stopped.size} 頭")
        sb.appendLine("- 停止（モデルが弾いた）: ${stopped.size} 頭")
        sb.appendLine()
        sb.appendLine("## 公開記録の粒度について")
        sb.appendLine()
        for (note in RECORD_GRANULARITY_NOTES) {
            sb.appendLine("- $note")
        }
        sb.appendLine()
        for (o in outcomes) {
            sb.appendLine("## ${o.fixtureName}")
            sb.appendLine()
            sb.appendLine("- 出典: ${o.sourceUrl}")
            if (o.stoppedAt == null) {
                sb.appendLine("- 結果: ✅ 一周完了")
            } else {
                sb.appendLine("- 結果: ⛔ ${o.stoppedAt} で停止")
                sb.appendLine("- 弾いた不変条件/エラー: `${o.stopReason}`")
            }
            sb.appendLine()
            sb.appendLine("### 合成した項目")
            sb.appendLine()
            for (note in o.synthesizedNotes) {
                sb.appendLine("- $note")
            }
            sb.appendLine()
            sb.appendLine("### 実行した段階")
            sb.appendLine()
            sb.appendLine("| 段階 | 結果 | 詳細 |")
            sb.appendLine("|------|------|------|")
            for (s in o.steps) {
                val mark = if (s.ok) "ok" else "NG"
                sb.appendLine("| ${s.step} | $mark | ${s.detail} |")
            }
            sb.appendLine()
        }
        return sb.toString()
    }

    fun write(outcomes: List<HorseReplayOutcome>, path: Path) {
        Files.createDirectories(path.parent)
        Files.writeString(path, render(outcomes))
    }
}
