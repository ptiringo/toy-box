package com.example.api.domain.sakamichi.model.member

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import org.jmolecules.ddd.annotation.ValueObject

/** 氏名が不変条件（姓・名とも非ブランク・50 文字以内）を満たさない。 */
data object InvalidMemberName

/**
 * メンバーの氏名（姓・名）。
 *
 * 姓・名それぞれ非ブランク・50 文字以内を不変条件とする。芸名・改名の扱いは今回スコープ外 （必要になった時点でモデリングする）。
 *
 * @property familyName 姓
 * @property givenName 名
 */
@ValueObject
@ConsistentCopyVisibility
data class MemberName private constructor(val familyName: String, val givenName: String) {
    companion object {
        private const val MAX_LENGTH = 50

        private fun isValid(part: String): Boolean = part.isNotEmpty() && part.length <= MAX_LENGTH

        /** 姓・名とも非ブランク・50 文字以内であることを検証して [MemberName] を生成する。 */
        fun create(familyName: String, givenName: String): Result<MemberName, InvalidMemberName> {
            val family = familyName.trim()
            val given = givenName.trim()
            return if (isValid(family) && isValid(given)) {
                Ok(MemberName(familyName = family, givenName = given))
            } else {
                Err(InvalidMemberName)
            }
        }
    }
}
