package com.example.api.domain.tennis

import com.fasterxml.uuid.Generators
import java.util.UUID

/** 選手ID */
@JvmInline
value class PlayerId(
    val value: UUID,
)

/** 選手 */
class Player(
    /** 名 */
    @Suppress("unused") val firstName: String,
    /** 姓 */
    @Suppress("unused") val lastName: String,
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

    override fun hashCode(): Int = id.hashCode()
}
