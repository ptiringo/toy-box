package com.example.api.domain.tennis.model

import com.example.api.domain.shared.generateId
import java.util.UUID
import org.jmolecules.ddd.annotation.AggregateRoot
import org.jmolecules.ddd.annotation.Identity
import org.jmolecules.ddd.annotation.ValueObject

/** 選手ID */
@ValueObject @JvmInline value class PlayerId(val value: UUID)

/** 選手 */
@AggregateRoot
class Player(
    /** 名 */
    @Suppress("unused") val firstName: String,
    /** 姓 */
    @Suppress("unused") val lastName: String,
) {
    @field:Identity val id = PlayerId(generateId())

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (javaClass != other?.javaClass) {
            return false
        }
        return id == (other as Player).id
    }

    override fun hashCode(): Int = id.hashCode()
}
