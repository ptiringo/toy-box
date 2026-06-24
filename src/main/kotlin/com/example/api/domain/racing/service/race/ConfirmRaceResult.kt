package com.example.api.domain.racing.service.race

import com.example.api.domain.racing.model.race.ConfirmRaceResultEvent
import com.example.api.domain.racing.model.race.RaceId
import com.example.api.domain.racing.model.race.RaceResult
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
