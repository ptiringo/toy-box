package com.example.api.domain.sakamichi

import com.example.api.domain.Entity
import com.fasterxml.uuid.Generators
import java.util.UUID

/** メンバーID */
@JvmInline value class MemberId(val value: UUID)

/** メンバー */
class Member(
    /** 名 */
    @Suppress("unused") val firstName: String,
    /** 姓 */
    @Suppress("unused") val lastName: String,
) : Entity<MemberId>() {
    /** メンバーID */
    override val id = MemberId(Generators.timeBasedEpochRandomGenerator().generate())
}
