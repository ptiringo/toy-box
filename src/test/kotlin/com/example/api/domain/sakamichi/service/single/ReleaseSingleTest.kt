package com.example.api.domain.sakamichi.service.single

import com.example.api.domain.sakamichi.model.group.Group
import com.example.api.domain.sakamichi.model.group.GroupName
import com.example.api.domain.sakamichi.model.member.Generation
import com.example.api.domain.sakamichi.model.member.Member
import com.example.api.domain.sakamichi.model.member.MemberName
import com.example.api.domain.sakamichi.model.release.FormationError
import com.example.api.domain.sakamichi.model.release.Position
import com.example.api.domain.sakamichi.model.release.ReleaseNumber
import com.example.api.domain.sakamichi.model.release.Track
import com.example.api.domain.sakamichi.model.release.TrackNumber
import com.example.api.domain.sakamichi.model.release.TrackTitle
import com.example.api.domain.sakamichi.model.release.Tracklist
import com.example.api.domain.sakamichi.model.single.SingleError
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import java.time.LocalDate
import org.junit.jupiter.api.Test

/** 選抜編成ドメインサービス releaseSingle の在籍チェック（集約またぎの前提条件）のユニットテスト。 */
class ReleaseSingleTest {
    private val group = Group.create(GroupName.create("乃木坂46").unwrap())
    private val otherGroup = Group.create(GroupName.create("櫻坂46").unwrap())
    private val number = ReleaseNumber.create(1).unwrap()
    private val tracklist =
        Tracklist.create(
                listOf(
                    Track(TrackNumber.create(1).unwrap(), TrackTitle.create("ぐるぐるかーてん").unwrap()),
                    Track(TrackNumber.create(2).unwrap(), TrackTitle.create("左胸の勇気").unwrap()),
                    Track(
                        TrackNumber.create(3).unwrap(),
                        TrackTitle.create("会いたかったかもしれない").unwrap(),
                    ),
                )
            )
            .unwrap()
    private val headlineTrackNumber = TrackNumber.create(1).unwrap()
    private val underTrack1 = TrackNumber.create(2).unwrap()
    private val underTrack2 = TrackNumber.create(3).unwrap()

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

        val single = releaseSingle(group, number, tracklist, headlineTrackNumber, lineup).unwrap()

        assert(single.groupId == group.id)
        assert(single.number == number)
        assert(single.tracklist == tracklist)
        assert(single.headlineTitle == TrackTitle.create("ぐるぐるかーてん").unwrap())
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

        val error = releaseSingle(group, number, tracklist, headlineTrackNumber, lineup).getError()

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

        val error = releaseSingle(group, number, tracklist, headlineTrackNumber, lineup).getError()

        assert(error == ReleaseSingleError.MembersNotInGroup(setOf(foreign.id)))
    }

    @Test
    fun `非選抜曲に他グループ在籍のメンバーが混じると MembersNotInGroup を返す`() {
        val foreign = activeMember(otherGroup, "森田")
        val lineup = listOf(Position.Center to activeMember(group, "齋藤"))
        val under = underTrack1 to listOf(Position.Center to foreign)

        val error =
            releaseSingle(
                    group,
                    number,
                    tracklist,
                    headlineTrackNumber,
                    lineup,
                    nonSenbatsuTracks = listOf(under),
                )
                .getError()

        assert(error == ReleaseSingleError.MembersNotInGroup(setOf(foreign.id)))
    }

    @Test
    fun `センター不在は InvalidSenbatsu に包んで返す`() {
        val lineup = listOf(spot(row = 1, numberInRow = 1) to activeMember(group))

        val error = releaseSingle(group, number, tracklist, headlineTrackNumber, lineup).getError()

        assert(error == ReleaseSingleError.InvalidSenbatsu(FormationError.CenterMissing))
    }

    @Test
    fun `同一メンバーの重複も InvalidSenbatsu に包んで返す`() {
        val duplicated = activeMember(group)
        val lineup =
            listOf(Position.Center to duplicated, spot(row = 1, numberInRow = 1) to duplicated)

        val error = releaseSingle(group, number, tracklist, headlineTrackNumber, lineup).getError()

        assert(
            error ==
                ReleaseSingleError.InvalidSenbatsu(
                    FormationError.DuplicateMember(setOf(duplicated.id))
                )
        )
    }

    @Test
    fun `見出しがトラックリストに無いと InvalidTrackComposition に包んで返す`() {
        val lineup = listOf(Position.Center to activeMember(group))

        val error =
            releaseSingle(group, number, tracklist, TrackNumber.create(9).unwrap(), lineup)
                .getError()

        assert(
            error ==
                ReleaseSingleError.InvalidTrackComposition(SingleError.HeadlineTrackNotInTracklist)
        )
    }

    @Test
    fun `非選抜曲を伴うシングルが成立する`() {
        val center = activeMember(group, "齋藤")
        val other = activeMember(group, "白石")
        val underCenter = activeMember(group, "秋元")
        val lineup = listOf(Position.Center to center, spot(row = 1, numberInRow = 1) to other)
        val under = underTrack1 to listOf(Position.Center to underCenter)

        val single =
            releaseSingle(group, number, tracklist, headlineTrackNumber, lineup, listOf(under))
                .unwrap()

        assert(single.senbatsu.memberIds == setOf(center.id, other.id))
        assert(single.nonSenbatsuTracks.size == 1)
        assert(single.nonSenbatsuTracks.single().trackNumber == underTrack1)
        assert(single.nonSenbatsuTracks.single().formation.centers == setOf(underCenter.id))
    }

    @Test
    fun `非選抜曲を2曲伴うシングルが成立する`() {
        val center = activeMember(group, "齋藤")
        val under1Center = activeMember(group, "秋元")
        val under2Center = activeMember(group, "高山")
        val lineup = listOf(Position.Center to center)
        val under1 = underTrack1 to listOf(Position.Center to under1Center)
        val under2 = underTrack2 to listOf(Position.Center to under2Center)

        val single =
            releaseSingle(
                    group,
                    number,
                    tracklist,
                    headlineTrackNumber,
                    lineup,
                    listOf(under1, under2),
                )
                .unwrap()

        assert(single.nonSenbatsuTracks.map { it.trackNumber } == listOf(underTrack1, underTrack2))
    }

    @Test
    fun `非選抜曲同士は同一メンバーの重複を許容する`() {
        val center = activeMember(group, "齋藤")
        val sharedUnder = activeMember(group, "秋元")
        val lineup = listOf(Position.Center to center)
        val under1 = underTrack1 to listOf(Position.Center to sharedUnder)
        val under2 = underTrack2 to listOf(Position.Center to sharedUnder)

        val single =
            releaseSingle(
                    group,
                    number,
                    tracklist,
                    headlineTrackNumber,
                    lineup,
                    listOf(under1, under2),
                )
                .unwrap()

        assert(single.nonSenbatsuTracks.size == 2)
        assert(single.nonSenbatsuTracks.all { it.formation.centers == setOf(sharedUnder.id) })
    }

    @Test
    fun `非選抜曲を渡さなければ nonSenbatsuTracks は空になる`() {
        val lineup = listOf(Position.Center to activeMember(group))

        val single = releaseSingle(group, number, tracklist, headlineTrackNumber, lineup).unwrap()

        assert(single.nonSenbatsuTracks.isEmpty())
    }

    @Test
    fun `同一メンバーが選抜と非選抜曲の両方にいると MembersInBothFormations を返す`() {
        val both = activeMember(group, "齋藤")
        val lineup =
            listOf(
                Position.Center to both,
                spot(row = 1, numberInRow = 1) to activeMember(group, "白石"),
            )
        val under = underTrack1 to listOf(Position.Center to both)

        val error =
            releaseSingle(group, number, tracklist, headlineTrackNumber, lineup, listOf(under))
                .getError()

        assert(error == ReleaseSingleError.MembersInBothFormations(setOf(both.id)))
    }

    @Test
    fun `非選抜曲に卒業済みメンバーが混じると MembersNotActive を返す`() {
        val graduated = graduatedMember(group, "西野")
        val lineup = listOf(Position.Center to activeMember(group))
        val under = underTrack1 to listOf(Position.Center to graduated)

        val error =
            releaseSingle(group, number, tracklist, headlineTrackNumber, lineup, listOf(under))
                .getError()

        assert(error == ReleaseSingleError.MembersNotActive(setOf(graduated.id)))
    }

    @Test
    fun `非選抜曲のセンター不在は InvalidNonSenbatsuTrack に包んで返す`() {
        val lineup = listOf(Position.Center to activeMember(group, "齋藤"))
        val under =
            underTrack1 to listOf(spot(row = 1, numberInRow = 1) to activeMember(group, "白石"))

        val error =
            releaseSingle(group, number, tracklist, headlineTrackNumber, lineup, listOf(under))
                .getError()

        assert(
            error ==
                ReleaseSingleError.InvalidNonSenbatsuTrack(
                    underTrack1,
                    FormationError.CenterMissing,
                )
        )
    }

    @Test
    fun `非選抜曲が見出しトラックと同じだと InvalidTrackComposition に包んで返す`() {
        val lineup = listOf(Position.Center to activeMember(group, "齋藤"))
        val under = headlineTrackNumber to listOf(Position.Center to activeMember(group, "白石"))

        val error =
            releaseSingle(group, number, tracklist, headlineTrackNumber, lineup, listOf(under))
                .getError()

        assert(
            error ==
                ReleaseSingleError.InvalidTrackComposition(
                    SingleError.NonSenbatsuTrackIsHeadline(setOf(headlineTrackNumber))
                )
        )
    }
}
