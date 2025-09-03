package com.example.api.domain.horse_racing.breeding

import java.util.*

/** 繁殖ID */
@JvmInline
value class BreedingId(val value: UUID)

/** 繁殖 */
@Suppress("unused")
class Breeding {
    /** 繁殖ID */
    val id = BreedingId(UUID.randomUUID())
}
