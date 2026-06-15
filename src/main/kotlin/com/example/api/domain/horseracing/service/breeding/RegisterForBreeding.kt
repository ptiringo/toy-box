package com.example.api.domain.horseracing.service.breeding

import com.example.api.domain.horseracing.model.breeding.BreedingRegistration
import com.example.api.domain.horseracing.model.breeding.BreedingRegistrationNumber
import com.example.api.domain.horseracing.model.horse.bloodhorse.BloodHorse
import com.example.api.domain.horseracing.model.horse.bloodhorse.Sex
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

/**
 * 血統登録済みの牝馬を繁殖の用に供するため繁殖登録する。
 *
 * 繁殖登録は牝馬のみが対象であり（種雄馬は種付証明書で扱う）、対象馬が種雌馬であることを検証してから [BreedingRegistration] を生成する。
 *
 * @param broodmare 繁殖登録する繁殖牝馬（血統登録済みの [BloodHorse]）
 * @param registrationNumber 交付される繁殖登録番号
 * @return 生成された [BreedingRegistration]、または前提条件違反を表す [BreedingRegistrationError]
 */
fun registerForBreeding(
    broodmare: BloodHorse,
    registrationNumber: BreedingRegistrationNumber,
): Result<BreedingRegistration, BreedingRegistrationError> =
    if (broodmare.sex != Sex.FEMALE) {
        Err(BreedingRegistrationError.NotFemale)
    } else {
        Ok(BreedingRegistration.of(registrationNumber, broodmare.id))
    }

/**
 * 繁殖登録の前提条件違反。
 *
 * 制度上の前提条件は複数ある（①血統登録済み ②馬名登録済み ③競走馬登録があれば抹消済み ④種雌馬であること）。このうち①血統登録済みは [BloodHorse]
 * の参照自体で担保され、②馬名登録・ ③競走馬登録抹消は対応する集約が未モデル化のため現時点では検証しない。集約が揃い次第バリアントを 追加できるよう sealed interface
 * としておく。
 */
sealed interface BreedingRegistrationError {
    /** 繁殖登録の対象は種雌馬（繁殖牝馬）に限られるが、対象馬が牝馬でない。 */
    data object NotFemale : BreedingRegistrationError
}
