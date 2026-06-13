package com.example.api.domain.horseracing.horse.bloodhorse

import com.example.api.domain.Entity
import java.util.UUID
import org.jmolecules.ddd.annotation.AggregateRoot
import org.jmolecules.ddd.annotation.Identity
import org.jmolecules.ddd.annotation.ValueObject

/** 軽種馬ID */
@ValueObject @JvmInline value class BloodHorseId(val value: UUID)

/** 性 */
@Suppress("unused")
enum class Sex {
    /** 雄 */
    MALE,

    /** 雌 */
    FEMALE,
}

/** 軽種馬 */
@AggregateRoot
class BloodHorse
private constructor(
    /** 性 */
    @Suppress("unused") val sex: Sex
) : Entity<BloodHorseId>() {
    /** 軽種馬ID */
    @field:Identity override val id = BloodHorseId(UUID.randomUUID())
}
