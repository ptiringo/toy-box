package com.example.api.domain.studbook.service.horse

import com.example.api.domain.studbook.model.breeding.BreedingResult
import com.example.api.domain.studbook.model.breeding.Covering
import com.example.api.domain.studbook.model.breeding.FoalingOutcome
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorse
import com.example.api.domain.studbook.model.horse.bloodhorse.DateOfBirth
import com.example.api.domain.studbook.model.horse.bloodhorse.FoalIdentity
import com.example.api.domain.studbook.model.horse.bloodhorse.PedigreeRegistrationNumber
import com.example.api.domain.studbook.model.horse.bloodhorse.RegisterInStudBookError
import com.example.api.domain.studbook.model.inspection.HorseInspection
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapError

/**
 * 持込馬（海外種付の産駒）を血統登録し、軽種馬（[BloodHorse]）を誕生させる。
 *
 * 繁殖成績（[BreedingResult]）の種付が海外種付（[Covering.Overseas]）で、分娩結果が生産（[FoalingOutcome.LiveFoal]）で
 * ある場合に、その産駒を持込馬として血統登録する入口（#624）。国内種付の産駒の [registerFoal] と対になる。
 * - 父 = 海外種付の外国種牡馬（`covering.sire`）。日本の繁殖登録を持たないので引き当てない
 * - 母 = 繁殖登録の繁殖牝馬（上位で解決して [dam] として渡す）
 * - 出生日 = 分娩結果（[FoalingOutcome.LiveFoal.foalingDate]）
 *
 * 判定の順は、種付が海外種付か（[RegisterBroughtInFoalError.NotOverseasCovering]）→ 生産か
 * （[RegisterBroughtInFoalError.NotLiveFoal]）→ 委譲先 [BloodHorse.createBroughtIn] の前提条件。
 *
 * @param breedingResult 産駒が生じた繁殖成績（種付が海外種付で、分娩結果が報告済みであること）
 * @param dam 母（雌）の軽種馬。繁殖登録の `registeredHorseId` に対応する個体を上位で解決して渡す
 * @param foalIdentity 申請者が持ち込む仔馬自身の個体識別情報（生年月日を除く）
 * @param inspection 確定済みの個体識別・親子判定審査
 * @param registrationNumber 交付される血統登録番号
 * @return 生成された [BloodHorse]（出自は持込馬）、または前提条件違反を表す [RegisterBroughtInFoalError]
 */
fun registerBroughtInFoal(
    breedingResult: BreedingResult,
    dam: BloodHorse,
    foalIdentity: FoalIdentity,
    inspection: HorseInspection,
    registrationNumber: PedigreeRegistrationNumber,
): Result<BloodHorse, RegisterBroughtInFoalError> {
    val covering = breedingResult.covering
    val outcome = breedingResult.outcome
    return when {
        covering !is Covering.Overseas ->
            Err(RegisterBroughtInFoalError.NotOverseasCovering(covering))
        outcome !is FoalingOutcome.LiveFoal -> Err(RegisterBroughtInFoalError.NotLiveFoal(outcome))
        else -> {
            val entry = foalIdentity.toStudBookEntry(DateOfBirth(outcome.foalingDate))
            BloodHorse.createBroughtIn(covering.sire, dam, entry, inspection, registrationNumber)
                .mapError { RegisterBroughtInFoalError.RegistrationFailed(it) }
        }
    }
}

/**
 * 持込馬の血統登録の前提条件違反。
 *
 * 失敗のしかたが複数あるため sealed interface とし、`when` の網羅性で漏れを防ぐ。
 */
sealed interface RegisterBroughtInFoalError {
    /**
     * 繁殖成績の種付が海外種付でない（国内種付、または種付せず）。国内種付の産駒は [registerFoal] で登録する。
     *
     * @property current 繁殖成績の種付。種付せずなら null
     */
    data class NotOverseasCovering(val current: Covering?) : RegisterBroughtInFoalError

    /**
     * 繁殖成績の分娩結果が生産（[FoalingOutcome.LiveFoal]）でない、または未報告（null）であり、登録できる産駒が存在しない。
     *
     * @property current 報告されている分娩結果。未報告なら null
     */
    data class NotLiveFoal(val current: FoalingOutcome?) : RegisterBroughtInFoalError

    /** 委譲先の血統登録（[BloodHorse.createBroughtIn]）の前提条件違反を wrap したもの。 */
    data class RegistrationFailed(val cause: RegisterInStudBookError) : RegisterBroughtInFoalError
}
