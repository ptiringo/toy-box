package com.example.api.domain.horse_racing

/** レースID */
@JvmInline
value class RaceId(val value: Long)

/** レース */
data class Race(
    /** ID */
    val id: RaceId,
    /** レース名 */
    val name: String,
)
