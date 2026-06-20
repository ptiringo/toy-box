package com.example.api.domain.horseracing.service.racing

import com.example.api.domain.horseracing.model.horse.bloodhorse.BloodHorse
import com.example.api.domain.horseracing.model.racing.RacingRegistration
import com.example.api.domain.horseracing.model.racing.RacingRegistrationNumber
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

/**
 * 血統登録・馬名登録済みの馬を競走の用に供するため競走馬登録する。
 *
 * 競走馬登録（出走資格）は JAIRS の管轄外（権威ソースは JRA・NAR）であり、JAIRS 規程に典拠を持つ前提条件が
 * 薄い（ADR-0012）。当面は当コンテキストで完結する前提条件として「馬名登録済みであること」を検証する。
 * 競走馬は馬名なしには出走・登録できないため、未命名の馬は競走馬登録の対象としない。血統登録済みは [BloodHorse] の参照自体で担保される。
 *
 * @param horse 競走馬登録する馬（血統登録済みの [BloodHorse]）
 * @param registrationNumber 交付される競走馬登録番号
 * @return 生成された [RacingRegistration]、または前提条件違反を表す [RegisterAsRacehorseError]
 */
fun registerAsRacehorse(
    horse: BloodHorse,
    registrationNumber: RacingRegistrationNumber,
): Result<RacingRegistration, RegisterAsRacehorseError> =
    if (horse.name == null) {
        Err(RegisterAsRacehorseError.NotNamed)
    } else {
        Ok(RacingRegistration.of(registrationNumber, horse.id))
    }

/**
 * 競走馬登録の前提条件違反。
 *
 * 制度上の前提条件は他にもありうる（競走馬登録は JAIRS 外で典拠が薄く、出走資格の詳細は外部権威に属する）。
 * 現時点では当コンテキストで検証できる「馬名登録済み」のみを扱うが、対応する集約が揃い次第バリアントを 追加できるよう sealed interface としておく。
 */
sealed interface RegisterAsRacehorseError {
    /** 競走馬登録の対象は馬名登録済みの馬に限られるが、対象馬が未命名。 */
    data object NotNamed : RegisterAsRacehorseError
}
