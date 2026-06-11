package com.example.api.domain.horseracing.horse.bloodhorse

import com.example.api.domain.Entity
import java.util.UUID

/** 軽種馬ID */
@JvmInline value class BloodHorseId(val value: UUID)

/** 性 */
@Suppress("unused")
enum class Sex {
    /** 雄 */
    MALE,

    /** 雌 */
    FEMALE,
}

/** 軽種馬 */
class BloodHorse
private constructor(
    /** 性 */
    @Suppress("unused") val sex: Sex
) : Entity<BloodHorseId>() {
    /** 軽種馬ID */
    override val id = BloodHorseId(UUID.randomUUID())
}
