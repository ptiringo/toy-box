package com.example.api.domain.horse_racing.race

import org.jmolecules.ddd.annotation.AggregateRoot
import org.jmolecules.ddd.annotation.ValueObject

/** レースID */
@ValueObject
@JvmInline
value class RaceId(val value: Long)

/** レース */
@AggregateRoot
data class Race(
    /** ID */
    val id: RaceId,
    /** レース名 */
    val name: String,
)
