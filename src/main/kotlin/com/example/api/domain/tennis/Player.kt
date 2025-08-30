package com.example.api.domain.tennis

import com.fasterxml.uuid.Generators
import java.util.*

/** 選手ID */
@JvmInline
value class PlayerId(val value: UUID)

/** 選手 */
data class Player(
    /** 名 */
    val firstName: String,
    /** 姓 */
    val lastName: String,
) {
    val id = PlayerId(Generators.timeBasedEpochRandomGenerator().generate())

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
