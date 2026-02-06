package com.example.api.domain.sakamichi

import com.fasterxml.uuid.Generators
import java.util.UUID

/** メンバーID */
@JvmInline
value class MemberId(
    val value: UUID,
)

/** メンバー */
class Member(
    /** 名 */
    @Suppress("unused") val firstName: String,
    /** 姓 */
    @Suppress("unused") val lastName: String,
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

    override fun hashCode(): Int = id.hashCode()
}
