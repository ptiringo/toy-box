package com.example.api.domain.studbook.service.horse

import com.example.api.domain.studbook.model.breeding.BreedingFixture
import com.example.api.domain.studbook.model.breeding.BreedingResult
import com.example.api.domain.studbook.model.breeding.FoalingOutcome
import com.example.api.domain.studbook.model.breeding.TwinFoal
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorse
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseFixture
import com.example.api.domain.studbook.model.horse.bloodhorse.BreedType
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
            registerFoal(
                    liveFoalResult(),
                    sire,
                    dam,
                    foalIdentity,
                    BloodHorseFixture.inspection(),
                    registrationNumber,
                    takenPositions = emptySet(),
                )
                .unwrap()

        assert(bloodHorse.origin == Origin.Domestic(sireId = sire.id, damId = dam.id))
        assert(bloodHorse.dateOfBirth.value == foalingDate)
        assert(bloodHorse.registrationNumber == registrationNumber)
    }

    @Test
    fun `海外種付の繁殖成績だと国内の経路では登録せず NotDomesticCovering を返すこと`() {
        val sire = BloodHorseFixture.bloodHorse(sex = Sex.MALE)
        val dam = BloodHorseFixture.bloodHorse(sex = Sex.FEMALE)
        val overseas =
            BreedingFixture.overseasBreedingResult()
                .recordFoaling(FoalingOutcome.LiveFoal(LocalDate.of(2000, 3, 21)))
                .unwrap()

        val result =
            registerFoal(
                overseas,
                sire,
                dam,
                BloodHorseFixture.foalIdentity(),
                BloodHorseFixture.inspection(),
                registrationNumber,
                takenPositions = emptySet(),
            )

        assert(result.getError() == RegisterFoalError.NotDomesticCovering)
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
                BloodHorseFixture.inspection(),
                registrationNumber,
                takenPositions = emptySet(),
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
                BloodHorseFixture.inspection(),
                registrationNumber,
                takenPositions = emptySet(),
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
                BloodHorseFixture.inspection(),
                registrationNumber,
                takenPositions = emptySet(),
            )

        assert(
            result.getError() ==
                RegisterFoalError.RegistrationFailed(RegisterInStudBookError.SireNotMale),
        )
    }

    private fun twinResult() =
        BreedingFixture.breedingResult()
            .recordFoaling(FoalingOutcome.LiveFoal(foalingDate, TwinFoal(foalingDate.plusDays(1))))
            .unwrap()

    private fun register(
        result: BreedingResult,
        taken: Set<FoalPosition>,
    ): Result<BloodHorse, RegisterFoalError> =
        registerFoal(
            result,
            BloodHorseFixture.bloodHorse(sex = Sex.MALE),
            BloodHorseFixture.bloodHorse(sex = Sex.FEMALE),
            BloodHorseFixture.foalIdentity(),
            BloodHorseFixture.inspection(),
            registrationNumber,
            takenPositions = taken,
        )

    @Test
    fun `双子でない生産の 1 頭目は PRIMARY 枠で登録される`() {
        val result = liveFoalResult()

        val foal = register(result, emptySet()).unwrap()

        assert(foal.bredFrom == FoalSlot(result.id, FoalPosition.PRIMARY))
    }

    @Test
    fun `双子でない生産で PRIMARY が埋まっていると FoalLimitReached(1)`() {
        val error = register(liveFoalResult(), setOf(FoalPosition.PRIMARY)).getError()

        assert(error == RegisterFoalError.FoalLimitReached(1))
    }

    @Test
    fun `双子の生産の 2 頭目は TWIN 枠でもう一頭の分娩日を生年月日にする`() {
        val result = twinResult()

        val foal = register(result, setOf(FoalPosition.PRIMARY)).unwrap()

        assert(foal.bredFrom == FoalSlot(result.id, FoalPosition.TWIN))
        assert(foal.dateOfBirth.value == LocalDate.of(2024, 3, 21))
    }

    @Test
    fun `双子の生産で両方の枠が埋まっていると FoalLimitReached(2)`() {
        val taken = setOf(FoalPosition.PRIMARY, FoalPosition.TWIN)

        val error = register(twinResult(), taken).getError()

        assert(error == RegisterFoalError.FoalLimitReached(2))
    }

    @Test
    fun `生産でない成績は枠より先に NotLiveFoal を返す`() {
        val error =
            register(BreedingFixture.breedingResult(), setOf(FoalPosition.PRIMARY)).getError()

        assert(error == RegisterFoalError.NotLiveFoal(null))
    }
}
