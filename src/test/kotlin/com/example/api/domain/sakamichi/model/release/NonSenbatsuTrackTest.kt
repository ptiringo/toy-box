package com.example.api.domain.sakamichi.model.release

import com.example.api.domain.sakamichi.model.member.MemberId
import com.example.api.domain.shared.generateId
import com.github.michaelbull.result.unwrap
import org.junit.jupiter.api.Test

/** 非選抜楽曲 VO NonSenbatsuTrack のユニットテスト。 */
class NonSenbatsuTrackTest {
    @Test
    fun `トラック番号と編成を保持する`() {
        val trackNumber = TrackNumber.create(2).unwrap()
        val formation =
            Formation.create(listOf(FormationSlot(Position.Center, MemberId(generateId()))))
                .unwrap()

        val nonSenbatsuTrack = NonSenbatsuTrack(trackNumber, formation)

        assert(nonSenbatsuTrack.trackNumber == trackNumber)
        assert(nonSenbatsuTrack.formation == formation)
    }

    @Test
    fun `同じトラック番号・編成なら等価`() {
        val trackNumber = TrackNumber.create(2).unwrap()
        val formation =
            Formation.create(listOf(FormationSlot(Position.Center, MemberId(generateId()))))
                .unwrap()

        assert(NonSenbatsuTrack(trackNumber, formation) == NonSenbatsuTrack(trackNumber, formation))
    }
}
