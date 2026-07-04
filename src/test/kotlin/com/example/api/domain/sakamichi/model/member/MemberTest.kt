package com.example.api.domain.sakamichi.model.member

import com.example.api.domain.sakamichi.model.group.Group
import com.example.api.domain.sakamichi.model.group.GroupName
import com.github.michaelbull.result.getError
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

    @Test
    fun `卒業すると卒業日を持つ Graduated になり新インスタンスが返る`() {
        val member = createMember()
        val graduatedOn = LocalDate.of(2023, 5, 18)

        val graduated = member.graduate(graduatedOn).unwrap()

        assert(graduated.membership == Membership.Graduated(graduatedOn))
        assert(graduated !== member)
    }

    @Test
    fun `卒業してもIDと加入時の属性は引き継がれる`() {
        val member = createMember()

        val graduated = member.graduate(LocalDate.of(2023, 5, 18)).unwrap()

        assert(graduated.id == member.id)
        assert(graduated.name == member.name)
        assert(graduated.groupId == member.groupId)
        assert(graduated.generation == member.generation)
        assert(graduated.joinedOn == member.joinedOn)
    }

    @Test
    fun `卒業しても元のインスタンスは在籍中のまま変わらない`() {
        val member = createMember()

        member.graduate(LocalDate.of(2023, 5, 18)).unwrap()

        assert(member.membership == Membership.Active)
    }

    @Test
    fun `二重卒業は AlreadyGraduated を返す`() {
        val graduatedOn = LocalDate.of(2023, 5, 18)
        val graduated = createMember().graduate(graduatedOn).unwrap()

        val error = graduated.graduate(LocalDate.of(2024, 1, 1)).getError()

        assert(error == GraduateError.AlreadyGraduated(graduatedOn))
    }

    @Test
    fun `加入日より前の卒業日は GraduatedBeforeJoined を返す`() {
        val member = createMember()

        val error = member.graduate(joinedOn.minusDays(1)).getError()

        assert(error == GraduateError.GraduatedBeforeJoined)
    }

    @Test
    fun `加入日当日の卒業は許可される`() {
        val member = createMember()

        val graduated = member.graduate(joinedOn).unwrap()

        assert(graduated.membership == Membership.Graduated(joinedOn))
    }
}
