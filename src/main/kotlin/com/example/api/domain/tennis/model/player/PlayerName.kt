package com.example.api.domain.tennis.model.player

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import org.jmolecules.ddd.annotation.ValueObject

/** 選手名が不変条件（姓・名とも非ブランク・50 文字以内）を満たさない。 */
data object InvalidPlayerName

/**
 * プロテニス選手の氏名（姓・名）。
 *
 * 姓・名それぞれ非ブランク・50 文字以内を不変条件とし、前後の空白は trim して正規化する（長さの判定も trim 後の値で行う）。
 * 表示名の組み立て（姓名の並び順）は表示側の関心のため、この値オブジェクトは保持しない。
 *
 * @property familyName 姓
 * @property givenName 名
 */
@ValueObject
@ConsistentCopyVisibility
data class PlayerName private constructor(val familyName: String, val givenName: String) {
    companion object {
        private const val MAX_LENGTH = 50

        private fun isValid(part: String): Boolean = part.isNotEmpty() && part.length <= MAX_LENGTH

        /** 姓・名とも非ブランク・50 文字以内であることを検証して [PlayerName] を生成する。 */
        fun create(familyName: String, givenName: String): Result<PlayerName, InvalidPlayerName> {
            val family = familyName.trim()
            val given = givenName.trim()
            return if (isValid(family) && isValid(given)) {
                Ok(PlayerName(familyName = family, givenName = given))
            } else {
                Err(InvalidPlayerName)
            }
        }
    }
}
