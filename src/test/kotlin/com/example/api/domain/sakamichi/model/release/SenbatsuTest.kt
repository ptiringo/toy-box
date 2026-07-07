package com.example.api.domain.sakamichi.model.release

import com.example.api.domain.sakamichi.model.member.MemberId
import com.example.api.domain.shared.generateId
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import org.junit.jupiter.api.Test

/** Senbatsu 値オブジェクトの不変条件（メンバー重複なし・Center 以外の立ち位置の定員 1 人・センター 1〜2 人）のユニットテスト。 */
class SenbatsuTest {
    private fun memberId(): MemberId = MemberId(generateId())

    private fun spot(row: Int, numberInRow: Int): Position.Spot =
        Position.Spot.create(row = row, numberInRow = numberInRow).unwrap()

    @Test
    fun `センターを含む選抜を編成できる`() {
        val centerMember = memberId()
        val otherMember = memberId()
        val slots =
            listOf(
                SenbatsuSlot(Position.Center, centerMember),
                SenbatsuSlot(spot(row = 1, numberInRow = 1), otherMember),
            )

        val senbatsu = Senbatsu.create(slots).unwrap()

        assert(senbatsu.slots == slots)
        assert(senbatsu.centers == setOf(centerMember))
        assert(senbatsu.memberIds == setOf(centerMember, otherMember))
    }

    @Test
    fun `センター1人のみの選抜も編成できる`() {
        val centerMember = memberId()

        val senbatsu = Senbatsu.create(listOf(SenbatsuSlot(Position.Center, centerMember))).unwrap()

        assert(senbatsu.centers == setOf(centerMember))
        assert(senbatsu.memberIds == setOf(centerMember))
    }

    @Test
    fun `W センター（2人）の選抜を編成できる`() {
        val center1 = memberId()
        val center2 = memberId()
        val slots =
            listOf(
                SenbatsuSlot(Position.Center, center1),
                SenbatsuSlot(Position.Center, center2),
                SenbatsuSlot(spot(row = 1, numberInRow = 1), memberId()),
            )

        val senbatsu = Senbatsu.create(slots).unwrap()

        assert(senbatsu.centers == setOf(center1, center2))
    }

    @Test
    fun `同一メンバーが複数の立ち位置に選ばれていると DuplicateMember を返す`() {
        val duplicated = memberId()
        val slots =
            listOf(
                SenbatsuSlot(Position.Center, duplicated),
                SenbatsuSlot(spot(row = 1, numberInRow = 1), duplicated),
                SenbatsuSlot(spot(row = 1, numberInRow = 2), memberId()),
            )

        val error = Senbatsu.create(slots).getError()

        assert(error == SenbatsuError.DuplicateMember(setOf(duplicated)))
    }

    @Test
    fun `同一の立ち位置に複数メンバーが割り当てられていると PositionOverCapacity を返す`() {
        val crowded = spot(row = 2, numberInRow = 1)
        val slots =
            listOf(
                SenbatsuSlot(Position.Center, memberId()),
                SenbatsuSlot(crowded, memberId()),
                SenbatsuSlot(crowded, memberId()),
            )

        val error = Senbatsu.create(slots).getError()

        assert(error == SenbatsuError.PositionOverCapacity(setOf(crowded)))
    }

    @Test
    fun `センターが不在なら CenterMissing を返す`() {
        val slots =
            listOf(
                SenbatsuSlot(spot(row = 1, numberInRow = 1), memberId()),
                SenbatsuSlot(spot(row = 1, numberInRow = 2), memberId()),
            )

        val error = Senbatsu.create(slots).getError()

        assert(error == SenbatsuError.CenterMissing)
    }

    @Test
    fun `センターが3人以上いると TooManyCenters を返す`() {
        val center1 = memberId()
        val center2 = memberId()
        val center3 = memberId()
        val slots =
            listOf(
                SenbatsuSlot(Position.Center, center1),
                SenbatsuSlot(Position.Center, center2),
                SenbatsuSlot(Position.Center, center3),
            )

        val error = Senbatsu.create(slots).getError()

        assert(error == SenbatsuError.TooManyCenters(setOf(center1, center2, center3)))
    }
}
