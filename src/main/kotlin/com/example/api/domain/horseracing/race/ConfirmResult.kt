package com.example.api.domain.horseracing.race

import com.github.michaelbull.result.Result

/** レースの確定 */
@Suppress("unused")
fun confirmRaceResult(
    raceResult: RaceResult
): Result<ConfirmRaceResultEvent, ConfirmRaceResultError> {
    TODO()
}

/** レース確定時に発生しうる業務ルール違反 */
sealed interface ConfirmRaceResultError {
    /** 当該レースの結果が既に確定済み */
    data class AlreadyConfirmed(val raceId: RaceId) : ConfirmRaceResultError

    /** 到達順位がレースのルール上不正 */
    data class InvalidOrderOfFinish(val reason: String) : ConfirmRaceResultError
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
    val something: Nothing
)

/** レース確定イベント */
data class ConfirmRaceResultEvent(
    // TODO: 正しい型を設計する
    val confirmedRaceResult: Nothing
)
