package com.example.api.domain.studbook.model.breeding

import com.example.api.domain.shared.createNonBlank
import com.github.michaelbull.result.Result
import org.jmolecules.ddd.annotation.ValueObject

/** 種付証明書番号がブランク。 */
data object BlankCoveringCertificateNumber

/**
 * 種付証明書番号。
 *
 * 種付の事実は種牡馬の管理者が発行する「種付証明書」で証明され、産駒の血統登録申込時の必須準備物となる（登録規程実施基準・血統登録のながれ）。
 * 種付という節目（イベント）が確かに行われたことの証憑を識別する。具体的な桁数・形式は未確認のため、現時点では ブランクでないことのみを不変条件とする （形式が判明したら桁数検証を加える）。
 *
 * @property value 種付証明書番号
 */
@ValueObject
@JvmInline
value class CoveringCertificateNumber private constructor(val value: String) {
    companion object {
        /** ブランクでないことを検証して [CoveringCertificateNumber] を生成する。 */
        fun create(
            value: String
        ): Result<CoveringCertificateNumber, BlankCoveringCertificateNumber> =
            createNonBlank(value, BlankCoveringCertificateNumber, ::CoveringCertificateNumber)
    }
}
