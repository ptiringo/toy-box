package com.example.api.domain.sakamichi.service.album

import com.example.api.domain.sakamichi.model.album.AlbumError
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
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import java.time.LocalDate
import org.junit.jupiter.api.Test

/** アルバム発売ドメインサービス releaseAlbum の在籍チェック（集約またぎの前提条件）のユニットテスト。 */
class ReleaseAlbumTest {
    private val group = Group.create(GroupName.create("乃木坂46").unwrap())
    private val otherGroup = Group.create(GroupName.create("櫻坂46").unwrap())
    private val number = ReleaseNumber.create(1).unwrap()
    private val tracklist =
        Tracklist.create(
                listOf(
                    Track(TrackNumber.create(1).unwrap(), TrackTitle.create("Time flies").unwrap()),
                    Track(TrackNumber.create(2).unwrap(), TrackTitle.create("僕は僕を好きになる").unwrap()),
                    Track(TrackNumber.create(3).unwrap(), TrackTitle.create("Sing Out!").unwrap()),
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
    fun `在籍中メンバーのみなら選抜が編成されアルバムが成立する`() {
        val centerMember = activeMember(group, "齋藤")
        val otherMember = activeMember(group, "白石")
        val lineup =
            listOf(Position.Center to centerMember, spot(row = 1, numberInRow = 1) to otherMember)

        val album = releaseAlbum(group, number, tracklist, headlineTrackNumber, lineup).unwrap()

        assert(album.groupId == group.id)
        assert(album.number == number)
        assert(album.tracklist == tracklist)
        assert(album.headlineTitle == TrackTitle.create("Time flies").unwrap())
        assert(album.senbatsu.centers == setOf(centerMember.id))
        assert(album.senbatsu.memberIds == setOf(centerMember.id, otherMember.id))
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

        val error = releaseAlbum(group, number, tracklist, headlineTrackNumber, lineup).getError()

        assert(error == ReleaseAlbumError.MembersNotActive(setOf(graduated1.id, graduated2.id)))
    }

    @Test
    fun `他グループ在籍のメンバーが混じると MembersNotInGroup を返す`() {
        val foreign = activeMember(otherGroup, "森田")
        val lineup =
            listOf(
                Position.Center to activeMember(group),
                spot(row = 1, numberInRow = 1) to foreign,
            )

        val error = releaseAlbum(group, number, tracklist, headlineTrackNumber, lineup).getError()

        assert(error == ReleaseAlbumError.MembersNotInGroup(setOf(foreign.id)))
    }

    @Test
    fun `センター不在は InvalidSenbatsu に包んで返す`() {
        val lineup = listOf(spot(row = 1, numberInRow = 1) to activeMember(group))

        val error = releaseAlbum(group, number, tracklist, headlineTrackNumber, lineup).getError()

        assert(error == ReleaseAlbumError.InvalidSenbatsu(FormationError.CenterMissing))
    }

    @Test
    fun `同一メンバーの重複も InvalidSenbatsu に包んで返す`() {
        val duplicated = activeMember(group)
        val lineup =
            listOf(Position.Center to duplicated, spot(row = 1, numberInRow = 1) to duplicated)

        val error = releaseAlbum(group, number, tracklist, headlineTrackNumber, lineup).getError()

        assert(
            error ==
                ReleaseAlbumError.InvalidSenbatsu(
                    FormationError.DuplicateMember(setOf(duplicated.id))
                )
        )
    }

    @Test
    fun `見出しがトラックリストに無いと InvalidTrackComposition に包んで返す`() {
        val lineup = listOf(Position.Center to activeMember(group))

        val error =
            releaseAlbum(group, number, tracklist, TrackNumber.create(9).unwrap(), lineup)
                .getError()

        assert(
            error ==
                ReleaseAlbumError.InvalidTrackComposition(AlbumError.HeadlineTrackNotInTracklist)
        )
    }

    @Test
    fun `非選抜曲を伴うアルバムが成立する`() {
        val center = activeMember(group, "齋藤")
        val underCenter = activeMember(group, "秋元")
        val lineup = listOf(Position.Center to center)
        val under = NonSenbatsuTrackLineup(underTrack1, listOf(Position.Center to underCenter))

        val album =
            releaseAlbum(group, number, tracklist, headlineTrackNumber, lineup, listOf(under))
                .unwrap()

        assert(album.nonSenbatsuTracks.size == 1)
        assert(album.nonSenbatsuTracks.single().trackNumber == underTrack1)
        assert(album.nonSenbatsuTracks.single().formation.centers == setOf(underCenter.id))
    }

    @Test
    fun `同一メンバーが選抜と非選抜曲の両方にいると MembersInBothFormations を返す`() {
        val both = activeMember(group, "齋藤")
        val lineup =
            listOf(
                Position.Center to both,
                spot(row = 1, numberInRow = 1) to activeMember(group, "白石"),
            )
        val under = NonSenbatsuTrackLineup(underTrack1, listOf(Position.Center to both))

        val error =
            releaseAlbum(group, number, tracklist, headlineTrackNumber, lineup, listOf(under))
                .getError()

        assert(error == ReleaseAlbumError.MembersInBothFormations(setOf(both.id)))
    }

    @Test
    fun `非選抜曲のセンター不在は InvalidNonSenbatsuTrack に包んで返す`() {
        val lineup = listOf(Position.Center to activeMember(group, "齋藤"))
        val under =
            NonSenbatsuTrackLineup(
                underTrack1,
                listOf(spot(row = 1, numberInRow = 1) to activeMember(group, "白石")),
            )

        val error =
            releaseAlbum(group, number, tracklist, headlineTrackNumber, lineup, listOf(under))
                .getError()

        assert(
            error ==
                ReleaseAlbumError.InvalidNonSenbatsuTrack(underTrack1, FormationError.CenterMissing)
        )
    }
}
