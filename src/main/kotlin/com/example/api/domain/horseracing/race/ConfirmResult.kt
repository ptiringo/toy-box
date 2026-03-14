package com.example.api.domain.horseracing.race

/**
 * レースの確定
 *
 * レース結果を確定し、確定イベントを返す
 *
 * 将来の実装では以下を考慮する必要がある：
 * - 着順の検証（同着の処理）
 * - 失格・取消の処理
 * - タイム記録の保存
 * - 賞金の計算
 */
@Suppress("unused")
fun confirmRaceResult(raceResult: RaceResult): Result<ConfirmRaceResultEvent> {
    TODO("レース結果確定機能は未実装です。将来のバージョンで実装予定です。")
}

/** レース結果 */
data class RaceResult(
    /** レースID */
    val raceId: RaceId,
    /** 到達順位 */
    val orderOfFinish: OrderOfFinish,
)

/**
 * 到達順位
 *
 * 将来の実装では以下の情報を含める予定：
 * - 着順（1位、2位、3位...）
 * - 馬番
 * - タイム
 * - 着差
 * - 通過順位
 * - 上がり3ハロン
 */
data class OrderOfFinish(
    // TODO: 正しい型を設計する
    // 例: val positions: List<PositionEntry>
    // data class PositionEntry(val position: Int, val horseNumber: Int, val time: Duration)
    val placeholder: Unit = Unit,
)

/**
 * レース確定イベント
 *
 * レース結果が確定したときに発行されるイベント
 *
 * 将来の実装では以下の情報を含める予定：
 * - 確定時刻
 * - 確定した着順
 * - 払戻金情報
 * - レース映像へのリンク
 */
data class ConfirmRaceResultEvent(
    // TODO: 正しい型を設計する
    // 例: val raceId: RaceId, val confirmedAt: LocalDateTime, val finalPositions: OrderOfFinish
    val placeholder: Unit = Unit,
)
