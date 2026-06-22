package com.example.api.domain.horseracing.model.breeding

import com.example.api.domain.horseracing.model.horse.bloodhorse.BloodHorseFixture
import com.example.api.domain.horseracing.model.horse.bloodhorse.Sex
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import java.time.LocalDate
import org.junit.jupiter.api.Test

/** [BreedingResult.create]（種付記録）のユニットテスト */
class BreedingResultCreateTest {
    private val coveringDate = LocalDate.of(2024, 4, 1)
    private val certificateNumber = CoveringCertificateNumber.create("C-2024-0001").unwrap()

    @Test
    fun `配合相手が種牡馬なら種付が記録され種牡馬を ID で参照する繁殖成績が生成されること`() {
        val registration = BreedingFixture.breedingRegistration()
        val stallion = BloodHorseFixture.bloodHorse(sex = Sex.MALE)

        val result =
            BreedingResult.create(registration, stallion, coveringDate, certificateNumber).unwrap()

        assert(result.breedingRegistrationId == registration.id)
        assert(result.covering.stallionId == stallion.id)
        assert(result.covering.coveringDate == coveringDate)
        assert(result.covering.certificateNumber == certificateNumber)
        assert(result.outcome == null)
    }

    @Test
    fun `配合相手が雄でないと StallionNotMale を返すこと`() {
        val registration = BreedingFixture.breedingRegistration()
        val mare = BloodHorseFixture.bloodHorse(sex = Sex.FEMALE)

        val result = BreedingResult.create(registration, mare, coveringDate, certificateNumber)

        assert(result.getError() == RecordCoveringError.StallionNotMale)
    }
}
