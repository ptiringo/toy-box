package com.example.api.domain.studbook.model.horse.bloodhorse

import com.example.api.domain.studbook.model.inspection.DnaParentageResult
import com.example.api.domain.studbook.model.inspection.ParentageDetermination
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import org.junit.jupiter.api.Test

/** [BloodHorse.create]（内国産馬の血統登録）のユニットテスト */
class BloodHorseCreateTest {
    private val registrationNumber = PedigreeRegistrationNumber.create("2023104567").unwrap()

    @Test
    fun `前提条件を満たすと血統登録され父母を ID で参照する BloodHorse が生成されること`() {
        val sire = BloodHorseFixture.bloodHorse(sex = Sex.MALE)
        val dam = BloodHorseFixture.bloodHorse(sex = Sex.FEMALE)
        val entry = BloodHorseFixture.studBookEntry(breedType = BreedType.THOROUGHBRED)
        val inspection = BloodHorseFixture.inspection()

        val bloodHorse =
            BloodHorse.create(sire, dam, entry, inspection, registrationNumber).unwrap()

        assert(bloodHorse.origin == Origin.Domestic(sireId = sire.id, damId = dam.id))
        assert(bloodHorse.breedType == BreedType.THOROUGHBRED)
        assert(bloodHorse.registrationNumber == registrationNumber)
        assert(bloodHorse.inspectionId == inspection.id)
    }

    @Test
    fun `父が雄でないと SireNotMale を返すこと`() {
        val sire = BloodHorseFixture.bloodHorse(sex = Sex.FEMALE)
        val dam = BloodHorseFixture.bloodHorse(sex = Sex.FEMALE)
        val entry = BloodHorseFixture.studBookEntry()

        val result =
            BloodHorse.create(sire, dam, entry, BloodHorseFixture.inspection(), registrationNumber)

        assert(result.getError() == RegisterInStudBookError.SireNotMale)
    }

    @Test
    fun `母が雌でないと DamNotFemale を返すこと`() {
        val sire = BloodHorseFixture.bloodHorse(sex = Sex.MALE)
        val dam = BloodHorseFixture.bloodHorse(sex = Sex.MALE)
        val entry = BloodHorseFixture.studBookEntry()

        val result =
            BloodHorse.create(sire, dam, entry, BloodHorseFixture.inspection(), registrationNumber)

        assert(result.getError() == RegisterInStudBookError.DamNotFemale)
    }

    @Test
    fun `DNA 親子判定が矛盾なし以外だと ParentageNotConfirmed を返すこと`() {
        val sire = BloodHorseFixture.bloodHorse(sex = Sex.MALE)
        val dam = BloodHorseFixture.bloodHorse(sex = Sex.FEMALE)
        val entry = BloodHorseFixture.studBookEntry()
        val inspection =
            BloodHorseFixture.inspection(
                parentage = ParentageDetermination.ByDna(DnaParentageResult.UNTESTED)
            )

        val result = BloodHorse.create(sire, dam, entry, inspection, registrationNumber)

        assert(result.getError() == RegisterInStudBookError.ParentageNotConfirmed)
    }

    @Test
    fun `サラブレッド種の仔の親がサラブレッド種でないと BreedMismatch を返すこと`() {
        val sire = BloodHorseFixture.bloodHorse(sex = Sex.MALE, breedType = BreedType.ANGLO_ARAB)
        val dam = BloodHorseFixture.bloodHorse(sex = Sex.FEMALE, breedType = BreedType.THOROUGHBRED)
        val entry = BloodHorseFixture.studBookEntry(breedType = BreedType.THOROUGHBRED)

        val result =
            BloodHorse.create(sire, dam, entry, BloodHorseFixture.inspection(), registrationNumber)

        assert(result.getError() == RegisterInStudBookError.BreedMismatch)
    }

    @Test
    fun `芦毛以外の父母の仔が芦毛だと GrayFoalFromNonGrayParents を返すこと`() {
        val sire = BloodHorseFixture.bloodHorse(sex = Sex.MALE, coatColor = CoatColor.BAY)
        val dam = BloodHorseFixture.bloodHorse(sex = Sex.FEMALE, coatColor = CoatColor.CHESTNUT)
        val entry = BloodHorseFixture.studBookEntry(coatColor = CoatColor.GRAY)

        val result =
            BloodHorse.create(sire, dam, entry, BloodHorseFixture.inspection(), registrationNumber)

        assert(result.getError() == RegisterInStudBookError.GrayFoalFromNonGrayParents)
    }

    @Test
    fun `栗毛同士の父母の仔が栗毛以外だと NonChestnutFoalFromChestnutParents を返すこと`() {
        val sire = BloodHorseFixture.bloodHorse(sex = Sex.MALE, coatColor = CoatColor.CHESTNUT)
        val dam =
            BloodHorseFixture.bloodHorse(sex = Sex.FEMALE, coatColor = CoatColor.DARK_CHESTNUT)
        val entry = BloodHorseFixture.studBookEntry(coatColor = CoatColor.BAY)

        val result =
            BloodHorse.create(sire, dam, entry, BloodHorseFixture.inspection(), registrationNumber)

        assert(result.getError() == RegisterInStudBookError.NonChestnutFoalFromChestnutParents)
    }

    @Test
    fun `父が芦毛なら仔が芦毛でも血統登録できること`() {
        val sire = BloodHorseFixture.bloodHorse(sex = Sex.MALE, coatColor = CoatColor.GRAY)
        val dam = BloodHorseFixture.bloodHorse(sex = Sex.FEMALE, coatColor = CoatColor.BAY)
        val entry = BloodHorseFixture.studBookEntry(coatColor = CoatColor.GRAY)

        val bloodHorse =
            BloodHorse.create(sire, dam, entry, BloodHorseFixture.inspection(), registrationNumber)
                .unwrap()

        assert(bloodHorse.coatColor == CoatColor.GRAY)
    }
}
