package com.example.api.domain.horseracing.race

import com.example.api.domain.Entity
import com.fasterxml.uuid.Generators
import java.util.UUID
import org.jmolecules.ddd.annotation.AggregateRoot
import org.jmolecules.ddd.annotation.Identity
import org.jmolecules.ddd.annotation.ValueObject

/** レースID */
@ValueObject @JvmInline value class RaceId(val value: UUID)

/** レース */
@AggregateRoot
class Race(
    /** レース名 */
    val name: String
) : Entity<RaceId>() {
    /** レースID */
    @field:Identity override val id = RaceId(Generators.timeBasedEpochRandomGenerator().generate())
}
