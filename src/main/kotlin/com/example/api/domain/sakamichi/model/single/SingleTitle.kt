package com.example.api.domain.sakamichi.model.single

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import org.jmolecules.ddd.annotation.ValueObject

/** シングル表題が不変条件（非ブランク・100 文字以内）を満たさない。 */
data object InvalidSingleTitle

/**
 * シングル表題（表題曲の曲名。例: ぐるぐるカーテン）。非ブランク・100 文字以内を不変条件とする。
 *
 * @property value シングル表題
 */
@ValueObject
@JvmInline
value class SingleTitle private constructor(val value: String) {
    companion object {
        private const val MAX_LENGTH = 100

        /** 非ブランク・100 文字以内であることを検証して [SingleTitle] を生成する。 */
        fun create(value: String): Result<SingleTitle, InvalidSingleTitle> {
            val trimmed = value.trim()
            return if (trimmed.isNotEmpty() && trimmed.length <= MAX_LENGTH) {
                Ok(SingleTitle(trimmed))
            } else {
                Err(InvalidSingleTitle)
            }
        }
    }
}
