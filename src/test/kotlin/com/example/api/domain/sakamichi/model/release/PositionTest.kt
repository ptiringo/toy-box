package com.example.api.domain.sakamichi.model.release

import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import org.junit.jupiter.api.Test

/** Position 値オブジェクトの不変条件（列・列内番号とも 1 以上）のユニットテスト。 */
class PositionTest {
    @Test
    fun `列・列内番号とも1以上なら立ち位置を生成できる`() {
        val spot = Position.Spot.create(row = 1, numberInRow = 2).unwrap()

        assert(spot.row == 1)
        assert(spot.numberInRow == 2)
    }

    @Test
    fun `列が1未満なら InvalidPosition を返す`() {
        assert(Position.Spot.create(row = 0, numberInRow = 1).getError() == InvalidPosition)
    }

    @Test
    fun `列内番号が1未満なら InvalidPosition を返す`() {
        assert(Position.Spot.create(row = 1, numberInRow = 0).getError() == InvalidPosition)
    }

    @Test
    fun `同じ列・列内番号の立ち位置は等価である`() {
        val a = Position.Spot.create(row = 2, numberInRow = 3).unwrap()
        val b = Position.Spot.create(row = 2, numberInRow = 3).unwrap()

        assert(a == b)
    }
}
