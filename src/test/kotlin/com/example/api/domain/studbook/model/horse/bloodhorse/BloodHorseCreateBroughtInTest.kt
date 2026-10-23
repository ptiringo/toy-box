package com.example.api.domain.studbook.model.horse.bloodhorse

import com.example.api.domain.studbook.model.inspection.DnaParentageResult
import com.example.api.domain.studbook.model.inspection.ParentageDetermination
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import org.junit.jupiter.api.Test

/** [BloodHorse.createBroughtIn]（持込馬の血統登録）のユニットテスト */
class BloodHorseCreateBroughtInTest {
    private val registrationNumber = PedigreeRegistrationNumber.create("2000104567").unwrap()
    private val sire = BloodHorseFixture.foreignSire()
    private val dam = BloodHorseFixture.bloodHorse(sex = Sex.FEMALE)

    @Test
    fun `持込馬として生成し出自は BroughtIn になる`() {
        val entry = BloodHorseFixture.studBookEntry(sex = Sex.FEMALE)
        val inspection = BloodHorseFixture.inspection()

        val bloodHorse =
            BloodHorse.createBroughtIn(sire, dam, entry, inspection, registrationNumber).unwrap()

        assert(bloodHorse.origin == Origin.BroughtIn(sire = sire, damId = dam.id))
        assert(bloodHorse.registrationNumber == registrationNumber)
        assert(bloodHorse.inspectionId == inspection.id)
        assert(bloodHorse.name == null)
    }

    @Test
    fun `母が雄だと DamNotFemale を返す`() {
        val result =
            BloodHorse.createBroughtIn(
                sire,
                BloodHorseFixture.bloodHorse(sex = Sex.MALE),
                BloodHorseFixture.studBookEntry(),
                BloodHorseFixture.inspection(),
                registrationNumber,
            )

        assert(result.getError() == RegisterInStudBookError.DamNotFemale)
    }

    @Test
    fun `DNA 親子判定が未確認だと ParentageNotConfirmed を返す`() {
        val result =
            BloodHorse.createBroughtIn(
                sire,
                dam,
                BloodHorseFixture.studBookEntry(),
                BloodHorseFixture.inspection(
                    parentage = ParentageDetermination.ByDna(DnaParentageResult.UNTESTED)
                ),
                registrationNumber,
            )

        assert(result.getError() == RegisterInStudBookError.ParentageNotConfirmed)
    }

    @Test
    fun `外国種牡馬と品種が整合しないと BreedMismatch を返す`() {
        val result =
            BloodHorse.createBroughtIn(
                BloodHorseFixture.foreignSire(breedType = BreedType.ARAB),
                dam,
                BloodHorseFixture.studBookEntry(breedType = BreedType.THOROUGHBRED),
                BloodHorseFixture.inspection(),
                registrationNumber,
            )

        assert(result.getError() == RegisterInStudBookError.BreedMismatch)
    }

    @Test
    fun `父母とも芦毛以外で芦毛の産駒は GrayFoalFromNonGrayParents を返す`() {
        val result =
            BloodHorse.createBroughtIn(
                BloodHorseFixture.foreignSire(coatColor = CoatColor.BAY),
                BloodHorseFixture.bloodHorse(sex = Sex.FEMALE, coatColor = CoatColor.BAY),
                BloodHorseFixture.studBookEntry(coatColor = CoatColor.GRAY),
                BloodHorseFixture.inspection(),
                registrationNumber,
            )

        assert(result.getError() == RegisterInStudBookError.GrayFoalFromNonGrayParents)
    }

    @Test
    fun `外国種牡馬が芦毛なら芦毛の産駒を受理する`() {
        val result =
            BloodHorse.createBroughtIn(
                BloodHorseFixture.foreignSire(coatColor = CoatColor.GRAY),
                BloodHorseFixture.bloodHorse(sex = Sex.FEMALE, coatColor = CoatColor.BAY),
                BloodHorseFixture.studBookEntry(coatColor = CoatColor.GRAY),
                BloodHorseFixture.inspection(),
                registrationNumber,
            )

        assert(result.unwrap().coatColor == CoatColor.GRAY)
    }

    @Test
    fun `外国種牡馬と母がともに栗毛で栗毛以外の産駒は NonChestnutFoalFromChestnutParents を返す`() {
        val result =
            BloodHorse.createBroughtIn(
                BloodHorseFixture.foreignSire(coatColor = CoatColor.CHESTNUT),
                BloodHorseFixture.bloodHorse(sex = Sex.FEMALE, coatColor = CoatColor.CHESTNUT),
                BloodHorseFixture.studBookEntry(coatColor = CoatColor.BAY),
                BloodHorseFixture.inspection(),
                registrationNumber,
            )

        assert(result.getError() == RegisterInStudBookError.NonChestnutFoalFromChestnutParents)
    }
}
