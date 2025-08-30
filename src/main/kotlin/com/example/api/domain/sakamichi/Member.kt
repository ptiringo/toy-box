package com.example.api.domain.sakamichi

import com.fasterxml.uuid.Generators
import java.util.*

/** メンバーID */
@JvmInline
value class MemberId(val value: UUID)

/** メンバー */
data class Member(
    /** 名 */
    val firstName: String,
    /** 姓 */
    val lastName: String,
) {
    /** メンバーID */
    val id = MemberId(Generators.timeBasedEpochRandomGenerator().generate())

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
