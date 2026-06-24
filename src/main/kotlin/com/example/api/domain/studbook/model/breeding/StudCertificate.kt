package com.example.api.domain.studbook.model.breeding

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import java.time.LocalDate
import org.jmolecules.ddd.annotation.ValueObject

/** 種畜証明書の不変条件違反。 */
sealed interface InvalidStudCertificate {
    /** 有効区域が 1 つも指定されていない。 */
    data object NoValidRegion : InvalidStudCertificate
}

/**
 * 種付が種畜証明書の有効性に反する（産駒の血統登録要件を満たさない）。
 *
 * 登録規程実施基準・第9条第1項(1)：種畜証明書を有する種雄馬の種付による産駒として血統登録できるのは、その種付が証明書に
 * 記載された**有効区域内かつ有効期間内**に行われた場合に限る。本エラーは種付がその条件を外れたことを表す。
 */
sealed interface CoveringValidityError {
    /**
     * 種付日が種畜証明書の有効期間外。
     *
     * @property coveringDate 検証対象の種付日
     * @property validPeriod 種畜証明書の有効期間
     */
    data class OutsideValidPeriod(val coveringDate: LocalDate, val validPeriod: ValidityPeriod) :
        CoveringValidityError

    /**
     * 種付場所が種畜証明書の有効区域外。
     *
     * @property coveringPlace 検証対象の種付場所
     * @property validRegions 種畜証明書の有効区域
     */
    data class OutsideValidRegion(
        val coveringPlace: BreedingRegion,
        val validRegions: Set<BreedingRegion>,
    ) : CoveringValidityError
}

/**
 * 種畜証明書。種雄馬が繁殖に供されることを証する書面で、有効区域と有効期間を記載する値オブジェクト。
 *
 * 種付が産駒の血統登録要件を満たすかは、その種付（日付・場所）がこの証明書の有効区域・有効期間の内側にあるかで定まる （登録規程実施基準・第9条第1項(1)）。判定は [authorizes]
 * が担う。有効区域は[BreedingRegion] の集合で表し、種付場所が その集合に含まれるかで区域整合を判定する（区域の包含関係はモデル対象外。[BreedingRegion] 参照）。
 *
 * 種畜証明書は種雄馬が持つ与件であり、種付記録の有効性検証に外から渡される。種付の事実を証する「種付証明書」 （[CoveringCertificateNumber]）とは別物である。
 *
 * @property number 種畜証明書番号
 * @property validRegions 有効区域（1 つ以上）
 * @property validPeriod 有効期間
 */
@ValueObject
@ConsistentCopyVisibility
data class StudCertificate
private constructor(
    val number: StudCertificateNumber,
    val validRegions: Set<BreedingRegion>,
    val validPeriod: ValidityPeriod,
) {
    /**
     * [coveringDate] に [coveringPlace] で行われた種付が、本証明書の有効区域・有効期間の内側にあるかを検証する。
     *
     * 有効期間外なら [CoveringValidityError.OutsideValidPeriod]、有効区域外なら
     * [CoveringValidityError.OutsideValidRegion] を返す。両方を満たすとき [Ok] を返す。
     *
     * @param coveringDate 種付日
     * @param coveringPlace 種付場所
     * @return 有効なら [Ok]、外れていれば該当する [CoveringValidityError]
     */
    fun authorizes(
        coveringDate: LocalDate,
        coveringPlace: BreedingRegion,
    ): Result<Unit, CoveringValidityError> =
        when {
            !validPeriod.contains(coveringDate) ->
                Err(CoveringValidityError.OutsideValidPeriod(coveringDate, validPeriod))
            coveringPlace !in validRegions ->
                Err(CoveringValidityError.OutsideValidRegion(coveringPlace, validRegions))
            else -> Ok(Unit)
        }

    companion object {
        /** 有効区域が 1 つ以上あることを検証して [StudCertificate] を生成する。 */
        fun create(
            number: StudCertificateNumber,
            validRegions: Set<BreedingRegion>,
            validPeriod: ValidityPeriod,
        ): Result<StudCertificate, InvalidStudCertificate> =
            if (validRegions.isEmpty()) Err(InvalidStudCertificate.NoValidRegion)
            else Ok(StudCertificate(number, validRegions, validPeriod))
    }
}
