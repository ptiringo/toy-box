package com.example.api.domain.tennis.model.player

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import org.jmolecules.ddd.annotation.ValueObject

/**
 * 国籍コードが ISO 3166-1 alpha-3 の形式（英字 3 文字）を満たさない。
 *
 * @property raw 正規化前の入力値
 */
data class InvalidCountryCode(val raw: String)

/**
 * 選手の国籍。ISO 3166-1 alpha-3 のコード（例: `JPN` / `ESP`）で表す。
 *
 * 不変条件は「英字 3 文字であること」の形式のみで、前後の空白を trim して大文字へ正規化する。
 * 実在するコードかどうかは検証しない（コード表の典拠を自前で保守する負担を避けるため。必要になった時点で 参照データとして別途モデリングする）。
 *
 * @property code 大文字 3 文字の国籍コード
 */
@ValueObject
@JvmInline
value class Country private constructor(val code: String) {
    companion object {
        private val ALPHA3 = Regex("[A-Za-z]{3}")

        /** ISO 3166-1 alpha-3 の形式であることを検証して [Country] を生成する。 */
        fun create(code: String): Result<Country, InvalidCountryCode> {
            val trimmed = code.trim()
            return if (ALPHA3.matches(trimmed)) {
                Ok(Country(trimmed.uppercase()))
            } else {
                Err(InvalidCountryCode(code))
            }
        }
    }
}
