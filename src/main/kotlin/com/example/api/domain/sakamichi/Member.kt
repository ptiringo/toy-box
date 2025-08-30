package com.example.api.domain.sakamichi

import org.jmolecules.ddd.annotation.AggregateRoot
import org.jmolecules.ddd.annotation.ValueObject

/** メンバーID */
@ValueObject
@JvmInline
value class MemberId(val value: Long)

/** メンバー */
@AggregateRoot
data class Member(
    /** メンバーID */
    val id: MemberId,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (javaClass != other?.javaClass) {
            return false
        }
        return id == (other as Member).id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}
