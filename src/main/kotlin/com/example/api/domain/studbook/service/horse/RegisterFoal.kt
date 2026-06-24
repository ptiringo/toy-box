package com.example.api.domain.studbook.service.horse

import com.example.api.domain.studbook.model.breeding.BreedingResult
import com.example.api.domain.studbook.model.breeding.FoalingOutcome
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorse
import com.example.api.domain.studbook.model.horse.bloodhorse.DateOfBirth
import com.example.api.domain.studbook.model.horse.bloodhorse.FoalIdentity
import com.example.api.domain.studbook.model.horse.bloodhorse.PedigreeRegistrationNumber
import com.example.api.domain.studbook.model.horse.bloodhorse.RegisterInStudBookError
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapError

/**
 * 生産産駒（生まれた仔馬）を血統登録し、軽種馬（[BloodHorse]）を誕生させる。
 *
 * 繁殖成績（[BreedingResult]）の分娩結果が生産（[FoalingOutcome.LiveFoal]＝産駒あり）である場合に、その産駒を
 * 血統登録する入口。父・母は繁殖記録から定まり、[BloodHorse.create] へ橋渡しする:
 * - 父（[sire]）= 種付（`covering.stallionId`）の種牡馬
 * - 母（[dam]）= 繁殖登録（`breedingRegistration.registeredHorseId`、ロールは繁殖牝馬）の繁殖牝馬
 * - 出生日 = 分娩結果（[FoalingOutcome.LiveFoal.foalingDate]）。申請者入力ではなく繁殖記録から確定する
 *
 * 産駒が生まれていない帰結（不受胎・流産・死産など [FoalingOutcome.LiveFoal] 以外）からは登録できず [RegisterFoalError.NotLiveFoal]
 * を返す。父=雄・母=雌・DNA 親子整合・親仔の品種整合といった前提条件の検証は委譲先の [BloodHorse.create] が担う。
 *
 * 父母が当システムに存在しない輸入馬・基礎輸入馬の登録経路は本サービスの対象外であり、別途設計する（#267）。
 *
 * @param breedingResult 産駒が生じた繁殖成績（分娩結果が報告済みであること）
 * @param sire 父（雄）の軽種馬。`breedingResult.covering.stallionId` に対応する個体を上位で解決して渡す
 * @param dam 母（雌）の軽種馬。繁殖登録の `registeredHorseId` に対応する個体を上位で解決して渡す
 * @param foalIdentity 申請者が持ち込む仔馬自身の個体識別情報（生年月日を除く）
 * @param registrationNumber 交付される血統登録番号
 * @return 生成された [BloodHorse]、または前提条件違反を表す [RegisterFoalError]
 */
fun registerFoal(
    breedingResult: BreedingResult,
    sire: BloodHorse,
    dam: BloodHorse,
    foalIdentity: FoalIdentity,
    registrationNumber: PedigreeRegistrationNumber,
): Result<BloodHorse, RegisterFoalError> {
    val outcome = breedingResult.outcome
    if (outcome !is FoalingOutcome.LiveFoal) {
        return Err(RegisterFoalError.NotLiveFoal(outcome))
    }
    val entry = foalIdentity.toStudBookEntry(DateOfBirth(outcome.foalingDate))
    return BloodHorse.create(sire, dam, entry, registrationNumber).mapError {
        RegisterFoalError.RegistrationFailed(it)
    }
}

/**
 * 生産産駒の血統登録の前提条件違反。
 *
 * 失敗のしかたが複数あるため sealed interface とし、`when` の網羅性で漏れを防ぐ。
 */
sealed interface RegisterFoalError {
    /**
     * 繁殖成績の分娩結果が生産（[FoalingOutcome.LiveFoal]）でない、または未報告（null）であり、登録できる産駒が存在しない。
     *
     * @property current 報告されている分娩結果。未報告なら null
     */
    data class NotLiveFoal(val current: FoalingOutcome?) : RegisterFoalError

    /**
     * 委譲先の血統登録（[BloodHorse.create]）の前提条件違反を wrap したもの。
     *
     * 個別バリアント（父が雄でない・品種不整合など）は [RegisterInStudBookError] を参照する。
     */
    data class RegistrationFailed(val cause: RegisterInStudBookError) : RegisterFoalError
}
