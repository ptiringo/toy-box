package com.example.api.domain.horseracing.model.horse.bloodhorse

import com.example.api.domain.shared.createNonBlank
import com.github.michaelbull.result.Result
import org.jmolecules.ddd.annotation.ValueObject

/** 血統登録番号がブランク。 */
data object BlankPedigreeRegistrationNumber

/**
 * 血統登録番号。
 *
 * 血統登録の成立時に交付される血統登録証明書に付される識別子。具体的な桁数・形式は未確認のため、現時点ではブランクでないことのみを不変条件とする（形式が判明したら桁数検証を加える）。
 *
 * @property value 血統登録番号
 */
@ValueObject
@JvmInline
value class PedigreeRegistrationNumber private constructor(val value: String) {
    companion object {
        /** ブランクでないことを検証して [PedigreeRegistrationNumber] を生成する。 */
        fun create(
            value: String
        ): Result<PedigreeRegistrationNumber, BlankPedigreeRegistrationNumber> =
            createNonBlank(value, BlankPedigreeRegistrationNumber, ::PedigreeRegistrationNumber)
    }
}
