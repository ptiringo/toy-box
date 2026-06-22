package com.example.api.domain.horseracing.service.horse

import com.example.api.domain.horseracing.model.breeding.BreedingFixture
import com.example.api.domain.horseracing.model.breeding.FoalingOutcome
import com.example.api.domain.horseracing.model.horse.bloodhorse.BloodHorseFixture
import com.example.api.domain.horseracing.model.horse.bloodhorse.BreedType
import com.example.api.domain.horseracing.model.horse.bloodhorse.PedigreeRegistrationNumber
import com.example.api.domain.horseracing.model.horse.bloodhorse.RegisterInStudBookError
import com.example.api.domain.horseracing.model.horse.bloodhorse.Sex
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import java.time.LocalDate
import org.junit.jupiter.api.Test

/** registerFoal ドメインサービスのユニットテスト */
class RegisterFoalTest {
    private val registrationNumber = PedigreeRegistrationNumber.create("2024104567").unwrap()
    private val foalingDate = LocalDate.of(2024, 3, 20)

    private fun liveFoalResult() =
        BreedingFixture.breedingResult()
            .recordFoaling(FoalingOutcome.LiveFoal(foalingDate))
            .unwrap()

    @Test
    fun `生産産駒は血統登録され父母を ID で参照し出生日が分娩日になること`() {
        val sire = BloodHorseFixture.bloodHorse(sex = Sex.MALE)
        val dam = BloodHorseFixture.bloodHorse(sex = Sex.FEMALE)
        val foalIdentity = BloodHorseFixture.foalIdentity(breedType = BreedType.THOROUGHBRED)

        val bloodHorse =
            registerFoal(liveFoalResult(), sire, dam, foalIdentity, registrationNumber).unwrap()

        assert(bloodHorse.sireId == sire.id)
        assert(bloodHorse.damId == dam.id)
        assert(bloodHorse.dateOfBirth.value == foalingDate)
        assert(bloodHorse.registrationNumber == registrationNumber)
    }

    @Test
    fun `分娩結果が未報告だと NotLiveFoal を返すこと`() {
        val sire = BloodHorseFixture.bloodHorse(sex = Sex.MALE)
        val dam = BloodHorseFixture.bloodHorse(sex = Sex.FEMALE)

        val result =
            registerFoal(
                BreedingFixture.breedingResult(),
                sire,
                dam,
                BloodHorseFixture.foalIdentity(),
                registrationNumber,
            )

        assert(result.getError() == RegisterFoalError.NotLiveFoal(null))
    }

    @Test
    fun `分娩結果が生産以外だと現在の帰結を添えて NotLiveFoal を返すこと`() {
        val sire = BloodHorseFixture.bloodHorse(sex = Sex.MALE)
        val dam = BloodHorseFixture.bloodHorse(sex = Sex.FEMALE)
        val notConceived =
            BreedingFixture.breedingResult().recordFoaling(FoalingOutcome.NotConceived).unwrap()

        val result =
            registerFoal(
                notConceived,
                sire,
                dam,
                BloodHorseFixture.foalIdentity(),
                registrationNumber,
            )

        assert(result.getError() == RegisterFoalError.NotLiveFoal(FoalingOutcome.NotConceived))
    }

    @Test
    fun `委譲先の前提条件違反は RegistrationFailed に wrap して返すこと`() {
        val sire = BloodHorseFixture.bloodHorse(sex = Sex.FEMALE)
        val dam = BloodHorseFixture.bloodHorse(sex = Sex.FEMALE)

        val result =
            registerFoal(
                liveFoalResult(),
                sire,
                dam,
                BloodHorseFixture.foalIdentity(),
                registrationNumber,
            )

        assert(
            result.getError() ==
                RegisterFoalError.RegistrationFailed(RegisterInStudBookError.SireNotMale)
        )
    }
}
