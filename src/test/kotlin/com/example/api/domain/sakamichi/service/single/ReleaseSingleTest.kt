package com.example.api.domain.sakamichi.service.single

import com.example.api.domain.sakamichi.model.group.Group
import com.example.api.domain.sakamichi.model.group.GroupName
import com.example.api.domain.sakamichi.model.member.Generation
import com.example.api.domain.sakamichi.model.member.Member
import com.example.api.domain.sakamichi.model.member.MemberName
import com.example.api.domain.sakamichi.model.release.Position
import com.example.api.domain.sakamichi.model.release.ReleaseNumber
import com.example.api.domain.sakamichi.model.release.SenbatsuError
import com.example.api.domain.sakamichi.model.single.SingleTitle
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import java.time.LocalDate
import org.junit.jupiter.api.Test

/** 選抜編成ドメインサービス releaseSingle の在籍チェック（集約またぎの前提条件）のユニットテスト。 */
class ReleaseSingleTest {
    private val group = Group.create(GroupName.create("乃木坂46").unwrap())
    private val otherGroup = Group.create(GroupName.create("櫻坂46").unwrap())
    private val number = ReleaseNumber.create(1).unwrap()
    private val title = SingleTitle.create("ぐるぐるカーテン").unwrap()

    private fun activeMember(group: Group, familyName: String = "齋藤"): Member =
        Member.create(
            name = MemberName.create(familyName, "飛鳥").unwrap(),
            groupId = group.id,
            generation = Generation.create(1).unwrap(),
            joinedOn = LocalDate.of(2011, 8, 21),
        )

    private fun graduatedMember(group: Group, familyName: String = "西野"): Member =
        activeMember(group, familyName).graduate(LocalDate.of(2023, 5, 18)).unwrap()

    private fun spot(row: Int, numberInRow: Int): Position.Spot =
        Position.Spot.create(row = row, numberInRow = numberInRow).unwrap()

    @Test
    fun `在籍中メンバーのみなら選抜が編成されシングルが成立する`() {
        val centerMember = activeMember(group, "齋藤")
        val otherMember = activeMember(group, "白石")
        val lineup =
            listOf(Position.Center to centerMember, spot(row = 1, numberInRow = 1) to otherMember)

        val single = releaseSingle(group, number, title, lineup).unwrap()

        assert(single.groupId == group.id)
        assert(single.number == number)
        assert(single.title == title)
        assert(single.senbatsu.centers == setOf(centerMember.id))
        assert(single.senbatsu.memberIds == setOf(centerMember.id, otherMember.id))
    }

    @Test
    fun `卒業済みメンバーが混じると MembersNotActive を返す`() {
        val graduated1 = graduatedMember(group, "西野")
        val graduated2 = graduatedMember(group, "生駒")
        val lineup =
            listOf(
                Position.Center to activeMember(group),
                spot(row = 1, numberInRow = 1) to graduated1,
                spot(row = 1, numberInRow = 2) to graduated2,
            )

        val error = releaseSingle(group, number, title, lineup).getError()

        assert(error == ReleaseSingleError.MembersNotActive(setOf(graduated1.id, graduated2.id)))
    }

    @Test
    fun `他グループ在籍のメンバーが混じると MembersNotInGroup を返す`() {
        val foreign = activeMember(otherGroup, "森田")
        val lineup =
            listOf(
                Position.Center to activeMember(group),
                spot(row = 1, numberInRow = 1) to foreign,
            )

        val error = releaseSingle(group, number, title, lineup).getError()

        assert(error == ReleaseSingleError.MembersNotInGroup(setOf(foreign.id)))
    }

    @Test
    fun `センター不在は InvalidSenbatsu に包んで返す`() {
        val lineup = listOf(spot(row = 1, numberInRow = 1) to activeMember(group))

        val error = releaseSingle(group, number, title, lineup).getError()

        assert(error == ReleaseSingleError.InvalidSenbatsu(SenbatsuError.CenterMissing))
    }

    @Test
    fun `同一メンバーの重複も InvalidSenbatsu に包んで返す`() {
        val duplicated = activeMember(group)
        val lineup =
            listOf(Position.Center to duplicated, spot(row = 1, numberInRow = 1) to duplicated)

        val error = releaseSingle(group, number, title, lineup).getError()

        assert(
            error ==
                ReleaseSingleError.InvalidSenbatsu(
                    SenbatsuError.DuplicateMember(setOf(duplicated.id))
                )
        )
    }
}
