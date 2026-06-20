package com.example.api.domain.horseracing.service.racing

import com.example.api.domain.horseracing.model.horse.bloodhorse.BloodHorseFixture
import com.example.api.domain.horseracing.model.horse.bloodhorse.HorseName
import com.example.api.domain.horseracing.model.racing.RacingRegistrationNumber
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import org.junit.jupiter.api.Test

/** registerAsRacehorse ドメインサービスのユニットテスト */
class RegisterAsRacehorseTest {
    private val registrationNumber = RacingRegistrationNumber.create("R2024001").unwrap()

    private fun namedHorse() =
        BloodHorseFixture.bloodHorse().assignName(HorseName.create("ナリタブライアン").unwrap()).unwrap()

    @Test
    fun `馬名登録済みの馬は競走馬登録され対象馬を ID で参照すること`() {
        val horse = namedHorse()

        val registration = registerAsRacehorse(horse, registrationNumber).unwrap()

        assert(registration.racehorseId == horse.id)
        assert(registration.registrationNumber == registrationNumber)
    }

    @Test
    fun `未命名の馬は NotNamed を返すこと`() {
        val horse = BloodHorseFixture.bloodHorse()

        val result = registerAsRacehorse(horse, registrationNumber)

        assert(result.getError() == RegisterAsRacehorseError.NotNamed)
    }
}
