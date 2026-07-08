package com.example.api.domain.sakamichi.model.single

import com.example.api.domain.sakamichi.model.group.Group
import com.example.api.domain.sakamichi.model.group.GroupName
import com.example.api.domain.sakamichi.model.member.MemberId
import com.example.api.domain.sakamichi.model.release.Formation
import com.example.api.domain.sakamichi.model.release.FormationSlot
import com.example.api.domain.sakamichi.model.release.Position
import com.example.api.domain.sakamichi.model.release.ReleaseNumber
import com.example.api.domain.sakamichi.model.release.Track
import com.example.api.domain.sakamichi.model.release.TrackNumber
import com.example.api.domain.sakamichi.model.release.TrackTitle
import com.example.api.domain.sakamichi.model.release.Tracklist
import com.example.api.domain.shared.generateId
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import org.junit.jupiter.api.Test

/** Single 集約の生成のユニットテスト。 */
class SingleTest {
    private val group = Group.create(GroupName.create("乃木坂46").unwrap())
    private val number = ReleaseNumber.create(1).unwrap()
    private val tracklist =
        Tracklist.create(
                listOf(
                    Track(TrackNumber.create(1).unwrap(), TrackTitle.create("ぐるぐるカーテン").unwrap()),
                    Track(TrackNumber.create(2).unwrap(), TrackTitle.create("左胸の勇気").unwrap()),
                )
            )
            .unwrap()
    private val headlineTrackNumber = TrackNumber.create(1).unwrap()
    private val senbatsu =
        Formation.create(listOf(FormationSlot(Position.Center, MemberId(generateId())))).unwrap()
    private val nonSenbatsu =
        Formation.create(listOf(FormationSlot(Position.Center, MemberId(generateId())))).unwrap()

    private fun createSingle(): Single =
        Single.create(
                groupId = group.id,
                number = number,
                tracklist = tracklist,
                headlineTrackNumber = headlineTrackNumber,
                senbatsu = senbatsu,
            )
            .unwrap()

    @Test
    fun `生成するとグループ・作品番号・トラックリスト・見出し・選抜を保持する`() {
        val single = createSingle()

        assert(single.groupId == group.id)
        assert(single.number == number)
        assert(single.tracklist == tracklist)
        assert(single.headlineTrackNumber == headlineTrackNumber)
        assert(single.senbatsu == senbatsu)
    }

    @Test
    fun `見出し曲名はトラックリストから導出される`() {
        assert(createSingle().headlineTitle == TrackTitle.create("ぐるぐるカーテン").unwrap())
    }

    @Test
    fun `見出しがトラックリストに無いと HeadlineTrackNotInTracklist を返す`() {
        val error =
            Single.create(
                    groupId = group.id,
                    number = number,
                    tracklist = tracklist,
                    headlineTrackNumber = TrackNumber.create(3).unwrap(),
                    senbatsu = senbatsu,
                )
                .getError()

        assert(error == SingleError.HeadlineTrackNotInTracklist)
    }

    @Test
    fun `生成のたびに異なるIDが採番される`() {
        assert(createSingle().id != createSingle().id)
    }

    @Test
    fun `非選抜編成を指定するとそれを保持する`() {
        val single =
            Single.create(
                    groupId = group.id,
                    number = number,
                    tracklist = tracklist,
                    headlineTrackNumber = headlineTrackNumber,
                    senbatsu = senbatsu,
                    nonSenbatsu = nonSenbatsu,
                )
                .unwrap()

        assert(single.nonSenbatsu == nonSenbatsu)
    }

    @Test
    fun `非選抜編成を指定しなければ null になる`() {
        assert(createSingle().nonSenbatsu == null)
    }
}
