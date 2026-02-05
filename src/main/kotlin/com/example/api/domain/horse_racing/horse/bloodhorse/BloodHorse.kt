package com.example.api.domain.horse_racing.horse.bloodhorse

import java.util.*

/** 軽種馬ID */
@JvmInline
value class BloodHorseId(
    val value: UUID,
)

/** 性 */
@Suppress("unused")
enum class Sex {
    /** 雄 */
    MALE,

    /** 雌 */
    FEMALE,
}

/** 軽種馬 */
class BloodHorse private constructor(
    /** 性 */
    @Suppress("unused") val sex: Sex,
) {
    /** 軽種馬ID */
    val id = BloodHorseId(UUID.randomUUID())

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (javaClass != other?.javaClass) {
            return false
        }
        return id == (other as BloodHorse).id
    }

    override fun hashCode(): Int = id.hashCode()
}
