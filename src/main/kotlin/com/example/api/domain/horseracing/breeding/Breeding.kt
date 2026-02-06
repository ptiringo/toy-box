package com.example.api.domain.horseracing.breeding

import java.util.UUID

/** 繁殖ID */
@JvmInline
value class BreedingId(
    val value: UUID,
)

/** 繁殖 */
@Suppress("unused")
class Breeding {
    /** 繁殖ID */
    val id = BreedingId(UUID.randomUUID())
}
