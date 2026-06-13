package com.example.api.domain.sakamichi.model

import com.example.api.domain.shared.Entity
import com.example.api.domain.shared.generateId
import java.util.UUID
import org.jmolecules.ddd.annotation.AggregateRoot
import org.jmolecules.ddd.annotation.Identity
import org.jmolecules.ddd.annotation.ValueObject

/** メンバーID */
@ValueObject @JvmInline value class MemberId(val value: UUID)

/** メンバー */
@AggregateRoot
class Member(
    /** 名 */
    @Suppress("unused") val firstName: String,
    /** 姓 */
    @Suppress("unused") val lastName: String,
) : Entity<MemberId>() {
    /** メンバーID */
    @field:Identity override val id = MemberId(generateId())
}
