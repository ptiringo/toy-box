package com.example.api.domain.horseracing.model.breeding

import com.example.api.domain.shared.createNonBlank
import com.github.michaelbull.result.Result
import org.jmolecules.ddd.annotation.ValueObject

/** 繁殖登録番号がブランク。 */
data object BlankBreedingRegistrationNumber

/**
 * 繁殖登録番号。
 *
 * 繁殖登録の成立時に交付される繁殖登録証明書に付される識別子。具体的な桁数・形式は未確認のため、現時点では
 * ブランクでないことのみを不変条件とする（形式が判明したら血統登録番号と同様に桁数検証を加える）。
 *
 * @property value 繁殖登録番号
 */
@ValueObject
@JvmInline
value class BreedingRegistrationNumber private constructor(val value: String) {
    companion object {
        /** ブランクでないことを検証して [BreedingRegistrationNumber] を生成する。 */
        fun create(
            value: String
        ): Result<BreedingRegistrationNumber, BlankBreedingRegistrationNumber> =
            createNonBlank(value, BlankBreedingRegistrationNumber, ::BreedingRegistrationNumber)
    }
}
