package com.example.api.replay

/** replay の各段階。種付を行った年と行わなかった年で通る段階が異なる。 */
enum class ReplayStep {
    REGISTER_STALLION,
    REGISTER_BROODMARE,
    REGISTER_STALLION_BREEDING,
    REGISTER_BROODMARE_BREEDING,
    RECORD_COVERING,
    RECORD_UNCOVERED,
    SUBMIT_COVERING_REPORT,
    REPORT_FOALING,
    REGISTER_FOAL,
    NAME_FOAL,
    SUBMIT_BREEDING_REPORT,
}

/** 1 段階の実行結果。ok=false のとき detail に弾いた 〜Error の文字列表現を入れる。 */
data class StepResult(val step: ReplayStep, val ok: Boolean, val detail: String)

/** 出典 1 本。[label] は何の記録か（繁殖牝馬・繁殖成績・種牡馬・産駒）。 */
data class SourceRef(val label: String, val url: String)

/**
 * 1 頭 × 1 シーズンの replay 観測結果。
 *
 * [stoppedAt] が非 null なら、その段階でモデルが実在馬を弾いた（＝突合レポートの発見）。 [synthesizedNotes]
 * は、公開記録に無いため合成した値とその理由。停止が無くても「事実に耐えた」とは 言えない（合成で埋めて通しているため）ので、レポートに必ず出す。 [sources]
 * はフィクスチャが持つ出典をすべて並べる（1 本に絞ると典拠の追跡性が落ちるため）。
 */
data class HorseReplayOutcome(
    val fixtureName: String,
    val sources: List<SourceRef>,
    val synthesizedNotes: List<String>,
    val steps: List<StepResult>,
    val stoppedAt: ReplayStep?,
    val stopReason: String?,
)
