package com.example.api.domain.studbook.model.breeding

import com.example.api.domain.shared.createNonBlank
import com.github.michaelbull.result.Result
import org.jmolecules.ddd.annotation.ValueObject

/** 海外種付証明の番号がブランク。 */
data object BlankForeignCoveringProofNumber

/**
 * 海外種付証明の番号。
 *
 * 持込馬の血統登録に添付する「母馬が種付けを受けた国の血統登録機関が発行した種付けの事実を証明する書類」（登録規程
 * 第8条第1項第4号）を識別する。国内種付の種付証明書（[CoveringCertificateNumber]）とは発行主体も根拠も別物である。
 * 形式は発行機関ごとに異なるため、ブランクでないことのみを不変条件とする。
 *
 * @property value 海外種付証明の番号
 */
@ValueObject
@JvmInline
value class ForeignCoveringProofNumber private constructor(val value: String) {
    companion object {
        /** ブランクでないことを検証して [ForeignCoveringProofNumber] を生成する。 */
        fun create(
            value: String
        ): Result<ForeignCoveringProofNumber, BlankForeignCoveringProofNumber> =
            createNonBlank(value, BlankForeignCoveringProofNumber, ::ForeignCoveringProofNumber)
    }
}
