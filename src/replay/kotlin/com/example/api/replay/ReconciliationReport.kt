package com.example.api.replay

import java.nio.file.Files
import java.nio.file.Path

/** replay 観測を Markdown の突合レポートに描画・書き出す。停止＝モデリングのバックログ。 */
object ReconciliationReport {
    fun render(outcomes: List<HorseReplayOutcome>): String {
        val sb = StringBuilder()
        sb.appendLine("# 繁殖 replay 突合レポート")
        sb.appendLine()
        val stopped = outcomes.filter { it.stoppedAt != null }
        sb.appendLine("- 対象: ${outcomes.size} 頭")
        sb.appendLine("- 一周完了: ${outcomes.size - stopped.size} 頭")
        sb.appendLine("- 停止（モデルが弾いた）: ${stopped.size} 頭")
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
