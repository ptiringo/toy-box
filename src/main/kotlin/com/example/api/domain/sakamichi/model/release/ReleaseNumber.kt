package com.example.api.domain.sakamichi.model.release

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import org.jmolecules.ddd.annotation.ValueObject

/** 作品番号が不変条件（1 以上）を満たさない。 */
data object InvalidReleaseNumber

/**
 * 作品番号（n 枚目）。シングル/アルバムで独立採番。1 以上の整数を不変条件とする。
 *
 * グループ内での連番であり、グループ横断で一意ではない（期生番号と同じ独立採番の考え方）。 「同一グループ内で作品番号が重複しない」は既存作品群をまたぐ集合制約のため、本 VO では守らない
 * （必要になった時点でドメインサービスへ切り出す。ADR-0022）。
 *
 * @property value 作品番号（1 以上）
 */
@ValueObject
@JvmInline
value class ReleaseNumber private constructor(val value: Int) {
    companion object {
        /** 1 以上であることを検証して [ReleaseNumber] を生成する。 */
        fun create(value: Int): Result<ReleaseNumber, InvalidReleaseNumber> =
            if (value >= 1) Ok(ReleaseNumber(value)) else Err(InvalidReleaseNumber)
    }
}
