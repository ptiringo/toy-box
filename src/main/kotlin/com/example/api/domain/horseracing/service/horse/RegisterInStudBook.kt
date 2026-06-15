package com.example.api.domain.horseracing.service.horse

import com.example.api.domain.horseracing.model.horse.bloodhorse.BloodHorse
import com.example.api.domain.horseracing.model.horse.bloodhorse.DnaParentageResult
import com.example.api.domain.horseracing.model.horse.bloodhorse.PedigreeRegistrationNumber
import com.example.api.domain.horseracing.model.horse.bloodhorse.Sex
import com.example.api.domain.horseracing.model.horse.bloodhorse.StudBookEntry
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

/**
 * 血統及び個体識別を明らかにする血統登録を行い、軽種馬（[BloodHorse]）を誕生させる。
 *
 * 父・母は既に血統登録済みの [BloodHorse] であり、本サービスは集約をまたぐ前提条件を検証してから [BloodHorse] を生成する。 検証する前提条件:
 * - 父が雄であること
 * - 母が雌であること
 * - 申告された父母との DNA 型による親子判定に矛盾がないこと
 * - 仔の品種が父母の品種と整合すること
 *
 * 生成された [BloodHorse] は父母を ID（`BloodHorseId`）経由で参照する。
 *
 * @param sire 父（雄）の軽種馬
 * @param dam 母（雌）の軽種馬
 * @param entry 仔馬自身の個体識別情報と DNA 親子判定結果
 * @param registrationNumber 交付される血統登録番号
 * @return 生成された [BloodHorse]、または前提条件違反を表す [RegisterInStudBookError]
 */
fun registerInStudBook(
    sire: BloodHorse,
    dam: BloodHorse,
    entry: StudBookEntry,
    registrationNumber: PedigreeRegistrationNumber,
): Result<BloodHorse, RegisterInStudBookError> =
    when {
        sire.sex != Sex.MALE -> Err(RegisterInStudBookError.SireNotMale)
        dam.sex != Sex.FEMALE -> Err(RegisterInStudBookError.DamNotFemale)
        entry.dnaParentage != DnaParentageResult.CONSISTENT ->
            Err(RegisterInStudBookError.ParentageNotConfirmed)
        !entry.breedType.isConsistentWith(sire.breedType, dam.breedType) ->
            Err(RegisterInStudBookError.BreedMismatch)
        else -> Ok(BloodHorse.of(entry, sire.id, dam.id, registrationNumber))
    }

/**
 * 血統登録の前提条件違反。
 *
 * 失敗のしかたが複数あるため sealed interface とし、`when` の網羅性で漏れを防ぐ。
 */
sealed interface RegisterInStudBookError {
    /** 父として指定された馬が雄でない。 */
    data object SireNotMale : RegisterInStudBookError

    /** 母として指定された馬が雌でない。 */
    data object DamNotFemale : RegisterInStudBookError

    /** 申告された父母との DNA 型による親子判定に矛盾がある、または未検査である。 */
    data object ParentageNotConfirmed : RegisterInStudBookError

    /** 仔の品種が父母の品種と整合しない。 */
    data object BreedMismatch : RegisterInStudBookError
}
