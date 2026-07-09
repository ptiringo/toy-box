package com.example.api.domain.sakamichi.model.album

import com.example.api.domain.sakamichi.model.group.GroupId
import com.example.api.domain.sakamichi.model.member.MemberId
import com.example.api.domain.sakamichi.model.release.Formation
import com.example.api.domain.sakamichi.model.release.FormationSlot
import com.example.api.domain.sakamichi.model.release.NonSenbatsuTrack
import com.example.api.domain.sakamichi.model.release.Position
import com.example.api.domain.sakamichi.model.release.ReleaseNumber
import com.example.api.domain.sakamichi.model.release.Track
import com.example.api.domain.sakamichi.model.release.TrackNumber
import com.example.api.domain.sakamichi.model.release.TrackTitle
import com.example.api.domain.sakamichi.model.release.Tracklist
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.getOrThrow
import java.util.UUID
import org.junit.jupiter.api.Test

class AlbumTest {
    private fun senbatsu(): Formation =
        Formation.create(listOf(FormationSlot(Position.Center, MemberId(UUID.randomUUID()))))
            .getOrThrow { AssertionError("fixture senbatsu should be valid") }

    private fun releaseNumber(n: Int): ReleaseNumber =
        ReleaseNumber.create(n).getOrThrow { AssertionError("fixture number should be valid") }

    private fun tracklist(): Tracklist =
        Tracklist.create(
                listOf(
                    Track(
                        TrackNumber.create(1).getOrThrow { AssertionError("n") },
                        TrackTitle.create("Time flies").getOrThrow { AssertionError("t") },
                    ),
                    Track(
                        TrackNumber.create(2).getOrThrow { AssertionError("n") },
                        TrackTitle.create("僕は僕を好きになる").getOrThrow { AssertionError("t") },
                    ),
                )
            )
            .getOrThrow { AssertionError("fixture tracklist should be valid") }

    private fun headline(n: Int): TrackNumber =
        TrackNumber.create(n).getOrThrow { AssertionError("fixture headline should be valid") }

    private fun nonSenbatsuTrack(n: Int): NonSenbatsuTrack =
        NonSenbatsuTrack(
            TrackNumber.create(n).getOrThrow { AssertionError("n") },
            Formation.create(listOf(FormationSlot(Position.Center, MemberId(UUID.randomUUID()))))
                .getOrThrow { AssertionError("f") },
        )

    @Test
    fun `create は渡した値をそのまま保持する`() {
        val groupId = GroupId(UUID.randomUUID())
        val number = releaseNumber(1)
        val tracklist = tracklist()
        val senbatsu = senbatsu()

        val album =
            Album.create(
                    groupId = groupId,
                    number = number,
                    tracklist = tracklist,
                    headlineTrackNumber = headline(2),
                    senbatsu = senbatsu,
                )
                .getOrThrow { AssertionError("valid album") }

        assert(album.groupId == groupId)
        assert(album.number == number)
        assert(album.tracklist == tracklist)
        assert(album.headlineTrackNumber == headline(2))
        assert(album.senbatsu == senbatsu)
    }

    @Test
    fun `見出し曲名はトラックリストから導出される`() {
        val album =
            Album.create(
                    GroupId(UUID.randomUUID()),
                    releaseNumber(1),
                    tracklist(),
                    headline(2),
                    senbatsu(),
                )
                .getOrThrow { AssertionError("valid album") }

        assert(
            album.headlineTitle == TrackTitle.create("僕は僕を好きになる").getOrThrow { AssertionError("t") }
        )
    }

    @Test
    fun `見出しがトラックリストに無いと HeadlineTrackNotInTracklist を返す`() {
        val error =
            Album.create(
                    GroupId(UUID.randomUUID()),
                    releaseNumber(1),
                    tracklist(),
                    headline(3),
                    senbatsu(),
                )
                .getError()

        assert(error == AlbumError.HeadlineTrackNotInTracklist)
    }

    @Test
    fun `create は毎回異なる AlbumId を採番する`() {
        val groupId = GroupId(UUID.randomUUID())
        val a =
            Album.create(groupId, releaseNumber(1), tracklist(), headline(1), senbatsu())
                .getOrThrow { AssertionError("valid") }
        val b =
            Album.create(groupId, releaseNumber(2), tracklist(), headline(1), senbatsu())
                .getOrThrow { AssertionError("valid") }
        assert(a.id != b.id)
    }

    @Test
    fun `非選抜曲を指定しなければ空リストになる`() {
        val album =
            Album.create(
                    GroupId(UUID.randomUUID()),
                    releaseNumber(1),
                    tracklist(),
                    headline(2),
                    senbatsu(),
                )
                .getOrThrow { AssertionError("valid") }

        assert(album.nonSenbatsuTracks.isEmpty())
    }

    @Test
    fun `非選抜曲を指定するとそれを保持する`() {
        val track = nonSenbatsuTrack(1)

        val album =
            Album.create(
                    GroupId(UUID.randomUUID()),
                    releaseNumber(1),
                    tracklist(),
                    headline(2),
                    senbatsu(),
                    nonSenbatsuTracks = listOf(track),
                )
                .getOrThrow { AssertionError("valid") }

        assert(album.nonSenbatsuTracks == listOf(track))
    }

    @Test
    fun `非選抜曲が見出しトラックと同じだと NonSenbatsuTrackIsHeadline を返す`() {
        val error =
            Album.create(
                    GroupId(UUID.randomUUID()),
                    releaseNumber(1),
                    tracklist(),
                    headline(2),
                    senbatsu(),
                    nonSenbatsuTracks = listOf(nonSenbatsuTrack(2)),
                )
                .getError()

        assert(
            error ==
                AlbumError.NonSenbatsuTrackIsHeadline(
                    setOf(TrackNumber.create(2).getOrThrow { AssertionError("n") })
                )
        )
    }

    @Test
    fun `非選抜曲がトラックリストに無いと NonSenbatsuTrackNotInTracklist を返す`() {
        val error =
            Album.create(
                    GroupId(UUID.randomUUID()),
                    releaseNumber(1),
                    tracklist(),
                    headline(2),
                    senbatsu(),
                    nonSenbatsuTracks = listOf(nonSenbatsuTrack(9)),
                )
                .getError()

        assert(
            error ==
                AlbumError.NonSenbatsuTrackNotInTracklist(
                    setOf(TrackNumber.create(9).getOrThrow { AssertionError("n") })
                )
        )
    }
}
