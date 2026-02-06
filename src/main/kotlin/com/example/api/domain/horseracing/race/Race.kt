package com.example.api.domain.horseracing.race

import com.fasterxml.uuid.Generators
import java.util.UUID

/** レースID */
@JvmInline
value class RaceId(
    val value: UUID,
)

/** レース */
class Race(
    /** レース名 */
    val name: String,
) {
    /** レースID */
    val id = RaceId(Generators.timeBasedEpochRandomGenerator().generate())

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (javaClass != other?.javaClass) {
            return false
        }
        return id == (other as Race).id
    }

    override fun hashCode(): Int = id.hashCode()
}
