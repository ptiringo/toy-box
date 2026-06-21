package com.example.api.domain.horseracing.service.breeding

import com.example.api.domain.horseracing.model.breeding.BreedingRegistrationNumber
import com.example.api.domain.horseracing.model.breeding.StallionRegistration
import com.example.api.domain.horseracing.model.horse.bloodhorse.BloodHorse
import com.example.api.domain.horseracing.model.horse.bloodhorse.Sex
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

/**
 * 血統登録済みの雄馬を繁殖の用に供するため種牡馬登録する。
 *
 * 繁殖登録（料金表上の「繁殖用 雄」）の雄側で、対象馬が雄であることを検証してから [StallionRegistration] を生成する。 雌側の繁殖登録（繁殖牝馬= Broodmare）は
 * registerForBreeding が担う。
 *
 * @param stallion 種牡馬登録する雄馬（血統登録済みの [BloodHorse]）
 * @param registrationNumber 交付される繁殖登録番号
 * @return 生成された [StallionRegistration]、または前提条件違反を表す [StallionRegistrationError]
 */
fun registerStallion(
    stallion: BloodHorse,
    registrationNumber: BreedingRegistrationNumber,
): Result<StallionRegistration, StallionRegistrationError> =
    if (stallion.sex != Sex.MALE) {
        Err(StallionRegistrationError.NotMale)
    } else {
        Ok(StallionRegistration.of(registrationNumber, stallion.id))
    }

/**
 * 種牡馬登録の前提条件違反。
 *
 * 制度上の前提条件は複数ある（①血統登録済み ②馬名登録済み ③競走馬登録があれば抹消済み ④雄であること）。このうち①血統登録済みは [BloodHorse]
 * の参照自体で担保され、②馬名登録・③競走馬登録抹消は対応する集約が未モデル化のため現時点では検証しない。 集約が揃い次第バリアントを追加できるよう sealed interface としておく。
 */
sealed interface StallionRegistrationError {
    /** 種牡馬登録の対象は雄馬（種牡馬）に限られるが、対象馬が雄でない。 */
    data object NotMale : StallionRegistrationError
}
