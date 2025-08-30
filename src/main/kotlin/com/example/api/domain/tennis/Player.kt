package com.example.api.domain.tennis

import org.jmolecules.ddd.annotation.AggregateRoot
import org.jmolecules.ddd.annotation.ValueObject

/** 選手ID */
@ValueObject
@JvmInline
value class PlayerId(val value: Long)

/** 選手 */
@AggregateRoot
data class Player(
    /** 選手ID */
    val id: PlayerId,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (javaClass != other?.javaClass) {
            return false
        }
        return id == (other as Player).id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}
