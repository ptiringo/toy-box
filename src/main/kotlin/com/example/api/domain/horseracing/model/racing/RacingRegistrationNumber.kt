package com.example.api.domain.horseracing.model.racing

import com.example.api.domain.shared.createNonBlank
import com.github.michaelbull.result.Result
import org.jmolecules.ddd.annotation.ValueObject

/** 競走馬登録番号がブランク。 */
data object BlankRacingRegistrationNumber

/**
 * 競走馬登録番号。
 *
 * 競走馬登録の成立時に交付される識別子。具体的な桁数・形式は未確認のため、現時点では ブランクでないことのみを不変条件とする（形式が判明したら桁数検証を加える）。
 *
 * @property value 競走馬登録番号
 */
@ValueObject
@JvmInline
value class RacingRegistrationNumber private constructor(val value: String) {
    companion object {
        /** ブランクでないことを検証して [RacingRegistrationNumber] を生成する。 */
        fun create(value: String): Result<RacingRegistrationNumber, BlankRacingRegistrationNumber> =
            createNonBlank(value, BlankRacingRegistrationNumber, ::RacingRegistrationNumber)
    }
}
