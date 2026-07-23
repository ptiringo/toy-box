package com.example.api.domain.studbook.model.horse.bloodhorse

import com.example.api.domain.studbook.model.inspection.ParentageDetermination
import com.github.michaelbull.result.unwrap
import org.junit.jupiter.api.Test

/** [BloodHorse.createCarriedOver]（移行取り込み）のユニットテスト */
class BloodHorseCreateCarriedOverTest {
    private val registrationNumber = PedigreeRegistrationNumber.create("2002100501").unwrap()

    @Test
    fun `移行取り込みの馬が血統登録され出自 CarriedOver を持ち父母 ID・原産国・揚陸日を持たないこと`() {
        val entry = BloodHorseFixture.carriedOverHorseEntry(sex = Sex.FEMALE)

        val bloodHorse =
            BloodHorse.createCarriedOver(
                entry,
                BloodHorseFixture.inspection(parentage = ParentageDetermination.NotApplicable),
                registrationNumber,
            )

        assert(bloodHorse.origin == Origin.CarriedOver)
        assert(bloodHorse.registrationNumber == registrationNumber)
        assert(bloodHorse.sex == Sex.FEMALE)
        assert(bloodHorse.name == null)
    }

    @Test
    fun `親の品種を問わず申告された品種がそのまま登録されること`() {
        // 父母・血統は先行原簿に記録済みで当システム外のため、品種整合は検証しない。
        val entry = BloodHorseFixture.carriedOverHorseEntry(breedType = BreedType.THOROUGHBRED)

        val bloodHorse =
            BloodHorse.createCarriedOver(
                entry,
                BloodHorseFixture.inspection(parentage = ParentageDetermination.NotApplicable),
                registrationNumber,
            )

        assert(bloodHorse.breedType == BreedType.THOROUGHBRED)
    }
}
