package com.example.api.domain.horseracing.model.breeding

import com.example.api.domain.shared.createNonBlank
import com.github.michaelbull.result.Result
import org.jmolecules.ddd.annotation.ValueObject

/** 繁殖の区域名がブランク。 */
data object BlankBreedingRegion

/**
 * 繁殖に関わる区域（地域）。
 *
 * 種畜証明書（[StudCertificate]）の有効区域、および種付が行われた場所を表すのに用いる。種付が産駒の血統登録要件を満たすには、
 * その種付が種畜証明書に記載された有効区域内で行われている必要がある（登録規程実施基準・第9条第1項(1)）。
 *
 * 一次資料には区域の具体的な分類（都道府県・全国などの体系）が明示されていないため、本モデルでは区域を**名前付きの値**として
 * 抽象化し、有効性は集合メンバーシップ（種付場所が有効区域の集合に含まれるか）で判定する。「全国」が個別区域を包含するといった 包含関係は本モデルの対象外とする。形式が判明したら具体化する。
 *
 * @property value 区域名
 */
@ValueObject
@JvmInline
value class BreedingRegion private constructor(val value: String) {
    companion object {
        /** ブランクでないことを検証して [BreedingRegion] を生成する。 */
        fun create(value: String): Result<BreedingRegion, BlankBreedingRegion> =
            createNonBlank(value, BlankBreedingRegion, ::BreedingRegion)
    }
}
