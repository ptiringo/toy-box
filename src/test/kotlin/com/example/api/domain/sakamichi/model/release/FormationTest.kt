package com.example.api.domain.sakamichi.model.release

import com.example.api.domain.sakamichi.model.member.MemberId
import com.example.api.domain.shared.generateId
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import org.junit.jupiter.api.Test

/** Formation 値オブジェクトの不変条件（メンバー重複なし・Center 以外の立ち位置の定員 1 人・センター 1〜2 人）のユニットテスト。 */
class FormationTest {
    private fun memberId(): MemberId = MemberId(generateId())

    private fun spot(row: Int, numberInRow: Int): Position.Spot =
        Position.Spot.create(row = row, numberInRow = numberInRow).unwrap()

    @Test
    fun `センターを含む選抜を編成できる`() {
        val centerMember = memberId()
        val otherMember = memberId()
        val slots =
            listOf(
                FormationSlot(Position.Center, centerMember),
                FormationSlot(spot(row = 1, numberInRow = 1), otherMember),
            )

        val formation = Formation.create(slots).unwrap()

        assert(formation.slots == slots)
        assert(formation.centers == setOf(centerMember))
        assert(formation.memberIds == setOf(centerMember, otherMember))
    }

    @Test
    fun `センター1人のみの選抜も編成できる`() {
        val centerMember = memberId()

        val formation =
            Formation.create(listOf(FormationSlot(Position.Center, centerMember))).unwrap()

        assert(formation.centers == setOf(centerMember))
        assert(formation.memberIds == setOf(centerMember))
    }

    @Test
    fun `W センター（2人）の選抜を編成できる`() {
        val center1 = memberId()
        val center2 = memberId()
        val slots =
            listOf(
                FormationSlot(Position.Center, center1),
                FormationSlot(Position.Center, center2),
                FormationSlot(spot(row = 1, numberInRow = 1), memberId()),
            )

        val formation = Formation.create(slots).unwrap()

        assert(formation.centers == setOf(center1, center2))
    }

    @Test
    fun `同一メンバーが複数の立ち位置に選ばれていると DuplicateMember を返す`() {
        val duplicated = memberId()
        val slots =
            listOf(
                FormationSlot(Position.Center, duplicated),
                FormationSlot(spot(row = 1, numberInRow = 1), duplicated),
                FormationSlot(spot(row = 1, numberInRow = 2), memberId()),
            )

        val error = Formation.create(slots).getError()

        assert(error == FormationError.DuplicateMember(setOf(duplicated)))
    }

    @Test
    fun `同一の立ち位置に複数メンバーが割り当てられていると PositionOverCapacity を返す`() {
        val crowded = spot(row = 2, numberInRow = 1)
        val slots =
            listOf(
                FormationSlot(Position.Center, memberId()),
                FormationSlot(crowded, memberId()),
                FormationSlot(crowded, memberId()),
            )

        val error = Formation.create(slots).getError()

        assert(error == FormationError.PositionOverCapacity(setOf(crowded)))
    }

    @Test
    fun `センターが不在なら CenterMissing を返す`() {
        val slots =
            listOf(
                FormationSlot(spot(row = 1, numberInRow = 1), memberId()),
                FormationSlot(spot(row = 1, numberInRow = 2), memberId()),
            )

        val error = Formation.create(slots).getError()

        assert(error == FormationError.CenterMissing)
    }

    @Test
    fun `センターが3人以上いると TooManyCenters を返す`() {
        val center1 = memberId()
        val center2 = memberId()
        val center3 = memberId()
        val slots =
            listOf(
                FormationSlot(Position.Center, center1),
                FormationSlot(Position.Center, center2),
                FormationSlot(Position.Center, center3),
            )

        val error = Formation.create(slots).getError()

        assert(error == FormationError.TooManyCenters(setOf(center1, center2, center3)))
    }
}
