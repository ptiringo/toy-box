package com.example.api.domain.horseracing.service.breeding

import com.example.api.domain.horseracing.model.breeding.BreedingRegistrationNumber
import com.example.api.domain.horseracing.model.horse.bloodhorse.BloodHorseFixture
import com.example.api.domain.horseracing.model.horse.bloodhorse.Sex
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import org.junit.jupiter.api.Test

/** registerStallion ドメインサービスのユニットテスト */
class RegisterStallionTest {
    private val registrationNumber = BreedingRegistrationNumber.create("B-2024-0002").unwrap()

    @Test
    fun `雄馬なら種牡馬登録が生成され種牡馬を ID で参照すること`() {
        val stallion = BloodHorseFixture.bloodHorse(sex = Sex.MALE)

        val result = registerStallion(stallion, registrationNumber).unwrap()

        assert(result.stallionId == stallion.id)
        assert(result.registrationNumber == registrationNumber)
    }

    @Test
    fun `対象馬が雄でないと NotMale を返すこと`() {
        val mare = BloodHorseFixture.bloodHorse(sex = Sex.FEMALE)

        val result = registerStallion(mare, registrationNumber)

        assert(result.getError() == StallionRegistrationError.NotMale)
    }
}
