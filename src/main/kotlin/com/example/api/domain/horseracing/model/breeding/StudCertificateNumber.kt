package com.example.api.domain.horseracing.model.breeding

import com.example.api.domain.shared.createNonBlank
import com.github.michaelbull.result.Result
import org.jmolecules.ddd.annotation.ValueObject

/** 種畜証明書番号がブランク。 */
data object BlankStudCertificateNumber

/**
 * 種畜証明書番号。
 *
 * 種畜証明書は種雄馬が繁殖に供されることを公的に証する書面で、有効区域・有効期間が記載される（登録規程第15条・実施基準第9条）。
 * 種付の事実を証する「種付証明書」（[CoveringCertificateNumber]）とは別物である点に注意。具体的な桁数・形式は未確認のため、
 * 現時点ではブランクでないことのみを不変条件とする（形式が判明したら桁数検証を加える）。
 *
 * @property value 種畜証明書番号
 */
@ValueObject
@JvmInline
value class StudCertificateNumber private constructor(val value: String) {
    companion object {
        /** ブランクでないことを検証して [StudCertificateNumber] を生成する。 */
        fun create(value: String): Result<StudCertificateNumber, BlankStudCertificateNumber> =
            createNonBlank(value, BlankStudCertificateNumber, ::StudCertificateNumber)
    }
}
