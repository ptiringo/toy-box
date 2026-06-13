package com.example.api.domain.horseracing.model.breeding

import com.example.api.domain.shared.generateId
import java.util.UUID
import org.jmolecules.ddd.annotation.AggregateRoot
import org.jmolecules.ddd.annotation.Identity
import org.jmolecules.ddd.annotation.ValueObject

/** 繁殖ID */
@ValueObject @JvmInline value class BreedingId(val value: UUID)

/** 繁殖 */
@Suppress("unused")
@AggregateRoot
class Breeding {
    /** 繁殖ID */
    @field:Identity val id = BreedingId(generateId())
}
