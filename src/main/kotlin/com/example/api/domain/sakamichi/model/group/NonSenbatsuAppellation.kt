package com.example.api.domain.sakamichi.model.group

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import org.jmolecules.ddd.annotation.ValueObject

/** 非選抜活動体の呼称が不変条件（非ブランク・100 文字以内）を満たさない。 */
data object InvalidNonSenbatsuAppellation

/**
 * 非選抜活動体の呼称。
 *
 * グループ別の非選抜編成の呼び名（乃木坂46=アンダー／櫻坂46=BACKS／日向坂46=ひなた坂46）。非ブランク・100 文字以内を 不変条件とする（[GroupName]
 * と同一方針）。どの作品から適用されたか等の時間軸は持たない（作品側の関心・#583）。
 *
 * @property value 呼称
 */
@ValueObject
@JvmInline
value class NonSenbatsuAppellation private constructor(val value: String) {
    companion object {
        private const val MAX_LENGTH = 100

        /** 非ブランク・100 文字以内であることを検証して [NonSenbatsuAppellation] を生成する。 */
        fun create(value: String): Result<NonSenbatsuAppellation, InvalidNonSenbatsuAppellation> {
            val trimmed = value.trim()
            return if (trimmed.isNotEmpty() && trimmed.length <= MAX_LENGTH) {
                Ok(NonSenbatsuAppellation(trimmed))
            } else {
                Err(InvalidNonSenbatsuAppellation)
            }
        }
    }
}
