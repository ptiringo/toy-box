package com.example.api.domain.horseracing.model.horse.bloodhorse

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
        val entry =
            BloodHorseFixture.studBookEntry(
                breedType = BreedType.THOROUGHBRED,
                dnaParentage = DnaParentageResult.CONSISTENT,
            )

        val bloodHorse = BloodHorse.create(sire, dam, entry, registrationNumber).unwrap()

        assert(bloodHorse.origin == Origin.Domestic(sireId = sire.id, damId = dam.id))
        assert(bloodHorse.breedType == BreedType.THOROUGHBRED)
        assert(bloodHorse.registrationNumber == registrationNumber)
    }

    @Test
    fun `父が雄でないと SireNotMale を返すこと`() {
        val sire = BloodHorseFixture.bloodHorse(sex = Sex.FEMALE)
        val dam = BloodHorseFixture.bloodHorse(sex = Sex.FEMALE)
        val entry = BloodHorseFixture.studBookEntry()

        val result = BloodHorse.create(sire, dam, entry, registrationNumber)

        assert(result.getError() == RegisterInStudBookError.SireNotMale)
    }

    @Test
    fun `母が雌でないと DamNotFemale を返すこと`() {
        val sire = BloodHorseFixture.bloodHorse(sex = Sex.MALE)
        val dam = BloodHorseFixture.bloodHorse(sex = Sex.MALE)
        val entry = BloodHorseFixture.studBookEntry()

        val result = BloodHorse.create(sire, dam, entry, registrationNumber)

        assert(result.getError() == RegisterInStudBookError.DamNotFemale)
    }

    @Test
    fun `DNA 親子判定が矛盾なし以外だと ParentageNotConfirmed を返すこと`() {
        val sire = BloodHorseFixture.bloodHorse(sex = Sex.MALE)
        val dam = BloodHorseFixture.bloodHorse(sex = Sex.FEMALE)
        val entry = BloodHorseFixture.studBookEntry(dnaParentage = DnaParentageResult.UNTESTED)

        val result = BloodHorse.create(sire, dam, entry, registrationNumber)

        assert(result.getError() == RegisterInStudBookError.ParentageNotConfirmed)
    }

    @Test
    fun `サラブレッド種の仔の親がサラブレッド種でないと BreedMismatch を返すこと`() {
        val sire = BloodHorseFixture.bloodHorse(sex = Sex.MALE, breedType = BreedType.ANGLO_ARAB)
        val dam = BloodHorseFixture.bloodHorse(sex = Sex.FEMALE, breedType = BreedType.THOROUGHBRED)
        val entry = BloodHorseFixture.studBookEntry(breedType = BreedType.THOROUGHBRED)

        val result = BloodHorse.create(sire, dam, entry, registrationNumber)

        assert(result.getError() == RegisterInStudBookError.BreedMismatch)
    }
}
