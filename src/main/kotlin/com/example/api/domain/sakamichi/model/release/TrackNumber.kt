package com.example.api.domain.sakamichi.model.release

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import org.jmolecules.ddd.annotation.ValueObject

/** トラック番号が不変条件（1 以上）を満たさない。 */
data object InvalidTrackNumber

/**
 * トラック番号（作品内の収録曲の通し番号。1 始まり）。1 以上の整数を不変条件とする。
 *
 * 作品（シングル/アルバム）内での連番であり、作品横断で一意ではない。「作品内で番号が 1..n の連番・重複なし」 であることは収録曲の集合をまたぐ制約のため本 VO
 * では守らず、[Tracklist.create] が検証する。
 *
 * @property value トラック番号（1 以上）
 */
@ValueObject
@JvmInline
value class TrackNumber private constructor(val value: Int) {
    companion object {
        /** 1 以上であることを検証して [TrackNumber] を生成する。 */
        fun create(value: Int): Result<TrackNumber, InvalidTrackNumber> =
            if (value >= 1) Ok(TrackNumber(value)) else Err(InvalidTrackNumber)
    }
}
