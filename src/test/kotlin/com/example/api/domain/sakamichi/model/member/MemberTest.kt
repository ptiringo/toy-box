package com.example.api.domain.sakamichi.model.member

import com.example.api.domain.sakamichi.model.group.Group
import com.example.api.domain.sakamichi.model.group.GroupName
import com.github.michaelbull.result.unwrap
import java.time.LocalDate
import org.junit.jupiter.api.Test

/** Member 集約の生成（＝加入）のユニットテスト。 */
class MemberTest {
    private val group = Group.create(GroupName.create("乃木坂46").unwrap())
    private val name = MemberName.create("齋藤", "飛鳥").unwrap()
    private val generation = Generation.create(1).unwrap()
    private val joinedOn = LocalDate.of(2011, 8, 21)

    private fun createMember(): Member =
        Member.create(name = name, groupId = group.id, generation = generation, joinedOn = joinedOn)

    @Test
    fun `生成すると在籍中でありグループ・期生・加入日を保持する`() {
        val member = createMember()

        assert(member.membership == Membership.Active)
        assert(member.name == name)
        assert(member.groupId == group.id)
        assert(member.generation == generation)
        assert(member.joinedOn == joinedOn)
    }

    @Test
    fun `生成のたびに異なるIDが採番される`() {
        assert(createMember().id != createMember().id)
    }
}
