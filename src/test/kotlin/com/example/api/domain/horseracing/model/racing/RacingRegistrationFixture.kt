package com.example.api.domain.horseracing.model.racing

import com.example.api.domain.horseracing.model.horse.bloodhorse.BloodHorseId
import com.example.api.domain.shared.generateId
import com.github.michaelbull.result.unwrap

/**
 * テスト用に [RacingRegistration] を組み立てる Object Mother。
 *
 * 生成口 [RacingRegistration.of] は本番では registerAsRacehorse に封じ込められている（internal）。テストでは
 * 前提条件検証を経ずに任意の競走馬登録を用意したいため、ここで直接 [RacingRegistration.of] を呼んで生成する。
 */
object RacingRegistrationFixture {
    /** 既定値を持つ [RacingRegistration] を生成する。必要な属性のみ上書きする。 */
    fun racingRegistration(
        racehorseId: BloodHorseId = BloodHorseId(generateId()),
        registrationNumber: String = "R2024001",
    ): RacingRegistration =
        RacingRegistration.of(
            registrationNumber = RacingRegistrationNumber.create(registrationNumber).unwrap(),
            racehorseId = racehorseId,
        )
}
