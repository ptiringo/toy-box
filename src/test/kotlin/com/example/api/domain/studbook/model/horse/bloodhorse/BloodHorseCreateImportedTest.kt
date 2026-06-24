package com.example.api.domain.studbook.model.horse.bloodhorse

import com.github.michaelbull.result.unwrap
import org.junit.jupiter.api.Test

/** [BloodHorse.createImported]（父母不明の輸入馬の血統登録）のユニットテスト */
class BloodHorseCreateImportedTest {
    private val registrationNumber = PedigreeRegistrationNumber.create("2020900001").unwrap()

    @Test
    fun `父母不明の輸入馬が血統登録され父母 ID を持たず原産国と揚陸日を持つこと`() {
        val entry = BloodHorseFixture.importedHorseEntry(originCountry = "アイルランド")

        val bloodHorse = BloodHorse.createImported(entry, registrationNumber)

        val expected =
            Origin.Imported(originCountry = entry.originCountry, landingDate = entry.landingDate)
        assert(bloodHorse.origin == expected)
        assert(bloodHorse.registrationNumber == registrationNumber)
    }

    @Test
    fun `親の品種を問わず申告された品種がそのまま登録されること`() {
        // 内国産馬と異なり父母の品種整合は検証しないため、サラブレッド種でも親なしで登録できる。
        val entry =
            BloodHorseFixture.importedHorseEntry(
                sex = Sex.FEMALE,
                breedType = BreedType.THOROUGHBRED,
            )

        val bloodHorse = BloodHorse.createImported(entry, registrationNumber)

        assert(bloodHorse.sex == Sex.FEMALE)
        assert(bloodHorse.breedType == BreedType.THOROUGHBRED)
        assert(bloodHorse.name == null)
    }
}
