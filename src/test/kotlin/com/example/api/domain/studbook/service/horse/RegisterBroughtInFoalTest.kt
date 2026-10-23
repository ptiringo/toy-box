package com.example.api.domain.studbook.service.horse

import com.example.api.domain.studbook.model.breeding.BreedingFixture
import com.example.api.domain.studbook.model.breeding.FoalingOutcome
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseFixture
import com.example.api.domain.studbook.model.horse.bloodhorse.DateOfBirth
import com.example.api.domain.studbook.model.horse.bloodhorse.Origin
import com.example.api.domain.studbook.model.horse.bloodhorse.PedigreeRegistrationNumber
import com.example.api.domain.studbook.model.horse.bloodhorse.RegisterInStudBookError
import com.example.api.domain.studbook.model.horse.bloodhorse.Sex
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import java.time.LocalDate
import org.junit.jupiter.api.Test

/** [registerBroughtInFoal]（持込馬の血統登録）ドメインサービスのユニットテスト */
class RegisterBroughtInFoalTest {
    private val registrationNumber = PedigreeRegistrationNumber.create("2000104567").unwrap()
    private val dam = BloodHorseFixture.bloodHorse(sex = Sex.FEMALE)
    private val sire = BloodHorseFixture.foreignSire()

    @Test
    fun `海外種付で生産なら持込馬を登録し出生日が分娩日になる`() {
        val breedingResult =
            BreedingFixture.overseasBreedingResult(sire = sire)
                .recordFoaling(FoalingOutcome.LiveFoal(LocalDate.of(2000, 3, 21)))
                .unwrap()

        val foal =
            registerBroughtInFoal(
                    breedingResult,
                    dam,
                    BloodHorseFixture.foalIdentity(sex = Sex.FEMALE),
                    BloodHorseFixture.inspection(),
                    registrationNumber,
                )
                .unwrap()

        assert(foal.origin == Origin.BroughtIn(sire = sire, damId = dam.id))
        assert(foal.dateOfBirth == DateOfBirth(LocalDate.of(2000, 3, 21)))
        assert(foal.sex == Sex.FEMALE)
    }

    @Test
    fun `国内種付の成績だと NotOverseasCovering を返す`() {
        val domestic = BreedingFixture.breedingResult()

        val result =
            registerBroughtInFoal(
                domestic,
                dam,
                BloodHorseFixture.foalIdentity(),
                BloodHorseFixture.inspection(),
                registrationNumber,
            )

        assert(
            result.getError() == RegisterBroughtInFoalError.NotOverseasCovering(domestic.covering)
        )
    }

    @Test
    fun `種付せずの成績だと NotOverseasCovering(null) を返す`() {
        val result =
            registerBroughtInFoal(
                BreedingFixture.uncoveredBreedingResult(),
                dam,
                BloodHorseFixture.foalIdentity(),
                BloodHorseFixture.inspection(),
                registrationNumber,
            )

        assert(result.getError() == RegisterBroughtInFoalError.NotOverseasCovering(null))
    }

    @Test
    fun `分娩結果が未報告だと NotLiveFoal(null) を返す`() {
        val result =
            registerBroughtInFoal(
                BreedingFixture.overseasBreedingResult(),
                dam,
                BloodHorseFixture.foalIdentity(),
                BloodHorseFixture.inspection(),
                registrationNumber,
            )

        assert(result.getError() == RegisterBroughtInFoalError.NotLiveFoal(null))
    }

    @Test
    fun `委譲先の前提条件違反は RegistrationFailed に wrap して返す`() {
        val breedingResult =
            BreedingFixture.overseasBreedingResult()
                .recordFoaling(FoalingOutcome.LiveFoal(LocalDate.of(2000, 3, 21)))
                .unwrap()

        val result =
            registerBroughtInFoal(
                breedingResult,
                BloodHorseFixture.bloodHorse(sex = Sex.MALE),
                BloodHorseFixture.foalIdentity(),
                BloodHorseFixture.inspection(),
                registrationNumber,
            )

        assert(
            result.getError() ==
                RegisterBroughtInFoalError.RegistrationFailed(RegisterInStudBookError.DamNotFemale)
        )
    }
}
