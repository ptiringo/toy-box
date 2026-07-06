package com.example.api.domain.sakamichi.model.single

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import org.jmolecules.ddd.annotation.ValueObject

/** 立ち位置が不変条件（列・列内番号とも 1 以上）を満たさない。 */
data object InvalidPosition

/**
 * 選抜フォーメーション上の立ち位置。
 *
 * センター（[Center]）とそれ以外の立ち位置（[Spot]）は相互排他であり、sealed interface で型として 強制する（`Membership`
 * と同じ流儀・ADR-0020）。立ち位置はシングルごとに変わる（グループやメンバーの 恒久属性ではない。sakamichi-sources §4）。
 */
@ValueObject
sealed interface Position {
    /** センター（フォーメーションの中心。選抜に 1〜2 人置く。2 人は W センター）。 */
    @ValueObject data object Center : Position

    /**
     * センター以外の立ち位置（列 × 列内番号）。
     *
     * 1 列目が最前列。列内番号は同一列の中での位置を表す。センターとの空間的な重なり（1 列目中央等）は 検証しない（探索段階の割り切り）。
     *
     * @property row 列（1 以上。1 列目が最前列）
     * @property numberInRow 列内番号（1 以上）
     */
    @ValueObject
    @ConsistentCopyVisibility
    data class Spot private constructor(val row: Int, val numberInRow: Int) : Position {
        companion object {
            /** 列・列内番号とも 1 以上であることを検証して [Spot] を生成する。 */
            fun create(row: Int, numberInRow: Int): Result<Spot, InvalidPosition> =
                if (row >= 1 && numberInRow >= 1) {
                    Ok(Spot(row = row, numberInRow = numberInRow))
                } else {
                    Err(InvalidPosition)
                }
        }
    }
}
