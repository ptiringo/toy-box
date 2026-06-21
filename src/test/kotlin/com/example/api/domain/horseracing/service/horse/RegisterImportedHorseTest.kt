package com.example.api.domain.horseracing.service.horse

import com.example.api.domain.horseracing.model.horse.bloodhorse.BloodHorseFixture
import com.example.api.domain.horseracing.model.horse.bloodhorse.BreedType
import com.example.api.domain.horseracing.model.horse.bloodhorse.PedigreeRegistrationNumber
import com.example.api.domain.horseracing.model.horse.bloodhorse.Sex
import com.github.michaelbull.result.unwrap
import org.junit.jupiter.api.Test

/** registerImportedHorse ドメインサービスのユニットテスト */
class RegisterImportedHorseTest {
    private val registrationNumber = PedigreeRegistrationNumber.create("2020900001").unwrap()

    @Test
    fun `父母不明の輸入馬が血統登録され父母 ID を持たず原産国と揚陸日を持つこと`() {
        val entry = BloodHorseFixture.importedHorseEntry(originCountry = "アイルランド")

        val bloodHorse = registerImportedHorse(entry, registrationNumber)

        assert(bloodHorse.sireId == null)
        assert(bloodHorse.damId == null)
        assert(bloodHorse.originCountry == entry.originCountry)
        assert(bloodHorse.landingDate == entry.landingDate)
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

        val bloodHorse = registerImportedHorse(entry, registrationNumber)

        assert(bloodHorse.sex == Sex.FEMALE)
        assert(bloodHorse.breedType == BreedType.THOROUGHBRED)
        assert(bloodHorse.name == null)
    }
}
