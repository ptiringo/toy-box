package com.example.api.domain.tennis

/** 選手ID */
@JvmInline
value class PlayerId(val value: Long)

/** 選手 */
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
