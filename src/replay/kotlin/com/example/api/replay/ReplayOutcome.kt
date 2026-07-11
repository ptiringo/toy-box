package com.example.api.replay

/** replay の各段階。 */
enum class ReplayStep {
    REGISTER_STALLION,
    REGISTER_BROODMARE,
    REGISTER_STALLION_BREEDING,
    REGISTER_BROODMARE_BREEDING,
    RECORD_COVERING,
    SUBMIT_COVERING_REPORT,
    REPORT_FOALING,
    REGISTER_FOAL,
    NAME_FOAL,
    SUBMIT_BREEDING_REPORT,
}

/** 1 段階の実行結果。ok=false のとき detail に弾いた 〜Error の文字列表現を入れる。 */
data class StepResult(val step: ReplayStep, val ok: Boolean, val detail: String)

/** 1 頭 × 1 シーズンの replay 観測結果。 [stoppedAt] が非 null なら、その段階でモデルが実在馬を弾いた（= 突合レポートの発見）。 */
data class HorseReplayOutcome(
    val fixtureName: String,
    val sourceUrl: String,
    val steps: List<StepResult>,
    val stoppedAt: ReplayStep?,
    val stopReason: String?,
)
