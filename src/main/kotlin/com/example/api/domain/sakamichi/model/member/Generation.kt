package com.example.api.domain.sakamichi.model.member

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import org.jmolecules.ddd.annotation.ValueObject

/** 期生が不変条件（1 以上）を満たさない。 */
data object InvalidGeneration

/**
 * 期生。
 *
 * 加入時期で区切るコホート（1期生・2期生…）。1 以上の整数を不変条件とする。加入時に固定され、 以後変わらない。**期生番号はグループごとに独立採番**であり、グループ横断で一意ではない
 * （乃木坂46 の 3期生と櫻坂46 の 3期生は無関係。sakamichi-sources §4）。
 *
 * @property value 期生番号（1 以上）
 */
@ValueObject
@JvmInline
value class Generation private constructor(val value: Int) {
    companion object {
        /** 1 以上であることを検証して [Generation] を生成する。 */
        fun create(value: Int): Result<Generation, InvalidGeneration> =
            if (value >= 1) Ok(Generation(value)) else Err(InvalidGeneration)
    }
}
