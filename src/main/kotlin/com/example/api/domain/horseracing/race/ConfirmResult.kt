package com.example.api.domain.horseracing.race

/** レースの確定 */
@Suppress("unused")
fun confirmRaceResult(raceResult: RaceResult): Result<ConfirmRaceResultEvent> {
    TODO()
}

/** レース結果 */
data class RaceResult(
    /** レースID */
    val raceId: RaceId,
    /** 到達順位 */
    val orderOfFinish: OrderOfFinish,
)

/** 到達順位 */
data class OrderOfFinish(
    // TODO: 正しい型を設計する
    val something: Nothing,
)

/** レース確定イベント */
data class ConfirmRaceResultEvent(
    // TODO: 正しい型を設計する
    val confirmedRaceResult: Nothing,
)
