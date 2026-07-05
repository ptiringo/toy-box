package com.example.api.domain.sakamichi.model.group

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import org.jmolecules.ddd.annotation.ValueObject

/** グループ名が不変条件（非ブランク・100 文字以内）を満たさない。 */
data object InvalidGroupName

/**
 * グループ名。
 *
 * 坂道シリーズのグループの名称（例: 乃木坂46）。非ブランク・100 文字以内を不変条件とする。 改名（欅坂46→櫻坂46 等）の状態遷移は今回スコープ外（スペック参照）。
 *
 * @property value グループ名
 */
@ValueObject
@JvmInline
value class GroupName private constructor(val value: String) {
    companion object {
        private const val MAX_LENGTH = 100

        /** 非ブランク・100 文字以内であることを検証して [GroupName] を生成する。 */
        fun create(value: String): Result<GroupName, InvalidGroupName> {
            val trimmed = value.trim()
            return if (trimmed.isNotEmpty() && trimmed.length <= MAX_LENGTH) {
                Ok(GroupName(trimmed))
            } else {
                Err(InvalidGroupName)
            }
        }
    }
}
