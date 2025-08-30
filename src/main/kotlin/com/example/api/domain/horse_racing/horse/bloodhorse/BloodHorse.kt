package com.example.api.domain.horse_racing.horse.bloodhorse

import org.jmolecules.ddd.annotation.AggregateRoot
import org.jmolecules.ddd.annotation.ValueObject
import java.util.*

/** 軽種馬ID */
@ValueObject
@JvmInline
value class BloodHorseId(val value: UUID)

/** 性 */
@ValueObject
@Suppress("unused")
enum class Sex {
    /** 雄 */
    MALE,

    /** 雌 */
    FEMALE;
}

/** 軽種馬 */
@AggregateRoot
@ConsistentCopyVisibility
data class BloodHorse private constructor(val sex: Sex) {
    val id: BloodHorseId = BloodHorseId(UUID.randomUUID())

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (javaClass != other?.javaClass) {
            return false
        }
        return id == (other as BloodHorse).id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}
