package com.example.api.domain.horseracing.race

import com.example.api.domain.Entity
import com.fasterxml.uuid.Generators
import java.util.UUID

/** レースID */
@JvmInline value class RaceId(val value: UUID)

/** レース */
class Race(
    /** レース名 */
    val name: String
) : Entity<RaceId>() {
    /** レースID */
    override val id = RaceId(Generators.timeBasedEpochRandomGenerator().generate())
}
