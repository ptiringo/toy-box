package com.example.api.domain.studbook.service.horse

import com.example.api.domain.studbook.model.breeding.BreedingFixture
import com.example.api.domain.studbook.model.breeding.BreedingResult
import com.example.api.domain.studbook.model.breeding.FoalingOutcome
import com.example.api.domain.studbook.model.breeding.TwinFoal
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorse
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseFixture
import com.example.api.domain.studbook.model.horse.bloodhorse.DateOfBirth
import com.example.api.domain.studbook.model.horse.bloodhorse.FoalPosition
import com.example.api.domain.studbook.model.horse.bloodhorse.FoalSlot
import com.example.api.domain.studbook.model.horse.bloodhorse.Origin
import com.example.api.domain.studbook.model.horse.bloodhorse.PedigreeRegistrationNumber
import com.example.api.domain.studbook.model.horse.bloodhorse.RegisterInStudBookError
import com.example.api.domain.studbook.model.horse.bloodhorse.Sex
import com.github.michaelbull.result.Result
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
                    takenPositions = emptySet(),
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
                takenPositions = emptySet(),
            )

        assert(
            result.getError() == RegisterBroughtInFoalError.NotOverseasCovering(domestic.covering),
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
                takenPositions = emptySet(),
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
                takenPositions = emptySet(),
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
                takenPositions = emptySet(),
            )

        assert(
            result.getError() ==
                RegisterBroughtInFoalError.RegistrationFailed(RegisterInStudBookError.DamNotFemale),
        )
    }

    private val foalingDate = LocalDate.of(2000, 3, 21)

    private fun overseasResult(twin: TwinFoal? = null) =
        BreedingFixture.overseasBreedingResult(sire = sire)
            .recordFoaling(FoalingOutcome.LiveFoal(foalingDate, twin))
            .unwrap()

    private fun register(
        result: BreedingResult,
        taken: Set<FoalPosition>,
    ): Result<BloodHorse, RegisterBroughtInFoalError> =
        registerBroughtInFoal(
            result,
            dam,
            BloodHorseFixture.foalIdentity(),
            BloodHorseFixture.inspection(),
            registrationNumber,
            takenPositions = taken,
        )

    @Test
    fun `双子でない生産の 1 頭目は PRIMARY 枠で登録される`() {
        val result = overseasResult()

        val foal = register(result, emptySet()).unwrap()

        assert(foal.bredFrom == FoalSlot(result.id, FoalPosition.PRIMARY))
    }

    @Test
    fun `双子でない生産で PRIMARY が埋まっていると FoalLimitReached(1)`() {
        val error = register(overseasResult(), setOf(FoalPosition.PRIMARY)).getError()

        assert(error == RegisterBroughtInFoalError.FoalLimitReached(1))
    }

    @Test
    fun `双子の生産の 2 頭目は TWIN 枠でもう一頭の分娩日を生年月日にする`() {
        val result = overseasResult(TwinFoal(LocalDate.of(2000, 3, 22)))

        val foal = register(result, setOf(FoalPosition.PRIMARY)).unwrap()

        assert(foal.bredFrom == FoalSlot(result.id, FoalPosition.TWIN))
        assert(foal.dateOfBirth == DateOfBirth(LocalDate.of(2000, 3, 22)))
    }

    @Test
    fun `双子の生産で両方の枠が埋まっていると FoalLimitReached(2)`() {
        val result = overseasResult(TwinFoal(foalingDate))
        val taken = setOf(FoalPosition.PRIMARY, FoalPosition.TWIN)

        assert(register(result, taken).getError() == RegisterBroughtInFoalError.FoalLimitReached(2))
    }

    @Test
    fun `生産でない成績は枠より先に NotLiveFoal を返す`() {
        val unreported = BreedingFixture.overseasBreedingResult(sire = sire)

        val error = register(unreported, setOf(FoalPosition.PRIMARY)).getError()

        assert(error == RegisterBroughtInFoalError.NotLiveFoal(null))
    }
}
