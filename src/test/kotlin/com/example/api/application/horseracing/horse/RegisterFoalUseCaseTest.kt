package com.example.api.application.horseracing.horse

import com.example.api.domain.horseracing.model.breeding.BreedingFixture
import com.example.api.domain.horseracing.model.breeding.BreedingRegistration
import com.example.api.domain.horseracing.model.breeding.BreedingRegistrationRepository
import com.example.api.domain.horseracing.model.breeding.BreedingResult
import com.example.api.domain.horseracing.model.breeding.BreedingResultId
import com.example.api.domain.horseracing.model.breeding.BreedingResultRepository
import com.example.api.domain.horseracing.model.breeding.FoalingOutcome
import com.example.api.domain.horseracing.model.horse.bloodhorse.BloodHorse
import com.example.api.domain.horseracing.model.horse.bloodhorse.BloodHorseFixture
import com.example.api.domain.horseracing.model.horse.bloodhorse.BloodHorseRepository
import com.example.api.domain.horseracing.model.horse.bloodhorse.BreedType
import com.example.api.domain.horseracing.model.horse.bloodhorse.CoatColor
import com.example.api.domain.horseracing.model.horse.bloodhorse.DnaParentageResult
import com.example.api.domain.horseracing.model.horse.bloodhorse.Origin
import com.example.api.domain.horseracing.model.horse.bloodhorse.RegisterInStudBookError
import com.example.api.domain.horseracing.model.horse.bloodhorse.Sex
import com.example.api.domain.horseracing.service.horse.RegisterFoalError
import com.example.api.domain.shared.Command
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class RegisterFoalUseCaseTest {

    private val foalingDate = LocalDate.of(2024, 3, 20)

    private fun command(
        breedingResultId: UUID,
        sex: Sex = Sex.MALE,
        breedType: BreedType = BreedType.THOROUGHBRED,
        microchipNumber: String = "392140000000001",
        breeder: String = "ノーザンファーム",
        registrationNumber: String = "2024104567",
    ): Command<RegisterFoalCommand> =
        Command(
            RegisterFoalCommand(
                breedingResultId = breedingResultId,
                sex = sex,
                coatColor = CoatColor.BAY,
                breedType = breedType,
                breeder = breeder,
                microchipNumber = microchipNumber,
                dnaParentage = DnaParentageResult.CONSISTENT,
                registrationNumber = registrationNumber,
            ),
            Instant.now(),
        )

    /** 父・母・繁殖登録・繁殖成績（生産済み）が解決できる正常系の土台を組む。 */
    private class Wiring {
        val sire: BloodHorse = BloodHorseFixture.bloodHorse(sex = Sex.MALE)
        val dam: BloodHorse = BloodHorseFixture.bloodHorse(sex = Sex.FEMALE)
        val breedingRegistration: BreedingRegistration =
            BreedingFixture.breedingRegistration(broodmare = dam)
        val breedingResult: BreedingResult =
            BreedingFixture.breedingResult(
                    broodmareRegistration = breedingRegistration,
                    stallionRegistration = BreedingFixture.stallionRegistration(stallion = sire),
                )
                .recordFoaling(FoalingOutcome.LiveFoal(LocalDate.of(2024, 3, 20)))
                .unwrap()

        val breedingResultRepository =
            mockk<BreedingResultRepository> {
                every { findById(breedingResult.id) } returns breedingResult
            }
        val breedingRegistrationRepository =
            mockk<BreedingRegistrationRepository> {
                every { findById(breedingRegistration.id) } returns breedingRegistration
            }
        val bloodHorseRepository =
            mockk<BloodHorseRepository> {
                every { findById(sire.id) } returns sire
                every { findById(dam.id) } returns dam
                every { save(any()) } answers { firstArg() }
            }

        fun useCase() =
            RegisterFoalUseCase(
                breedingResultRepository,
                breedingRegistrationRepository,
                bloodHorseRepository,
            )
    }

    @Nested
    inner class SuccessCase {
        @Test
        fun `産駒が血統登録され父母が繁殖記録から解決され出生日が分娩日になる`() {
            val w = Wiring()

            val bloodHorse = w.useCase()(command(w.breedingResult.id.value)).unwrap()

            assert(bloodHorse.origin == Origin.Domestic(sireId = w.sire.id, damId = w.dam.id))
            assert(bloodHorse.dateOfBirth.value == foalingDate)
            verify(exactly = 1) { w.bloodHorseRepository.save(any()) }
        }
    }

    @Nested
    inner class ValidationFailureCase {
        @Test
        fun `血統登録番号がブランクだと InvalidRegistrationNumber を返す`() {
            val w = Wiring()

            val result = w.useCase()(command(w.breedingResult.id.value, registrationNumber = ""))

            assert(result.getError() == RegisterFoalUseCaseError.InvalidRegistrationNumber)
            verify(exactly = 0) { w.bloodHorseRepository.save(any()) }
        }

        @Test
        fun `マイクロチップ番号が不正だと InvalidMicrochipNumber を返す`() {
            val w = Wiring()

            val result = w.useCase()(command(w.breedingResult.id.value, microchipNumber = "123"))

            assert(result.getError() == RegisterFoalUseCaseError.InvalidMicrochipNumber)
            verify(exactly = 0) { w.bloodHorseRepository.save(any()) }
        }

        @Test
        fun `生産者名がブランクだと BlankBreeder を返す`() {
            val w = Wiring()

            val result = w.useCase()(command(w.breedingResult.id.value, breeder = ""))

            assert(result.getError() == RegisterFoalUseCaseError.BlankBreeder)
            verify(exactly = 0) { w.bloodHorseRepository.save(any()) }
        }
    }

    @Nested
    inner class LookupFailureCase {
        @Test
        fun `繁殖成績が見つからないと BreedingResultNotFound を返し永続化されない`() {
            val breedingResultId = UUID.randomUUID()
            val repository =
                mockk<BreedingResultRepository> {
                    every { findById(BreedingResultId(breedingResultId)) } returns null
                }
            val useCase = RegisterFoalUseCase(repository, mockk(), mockk(relaxed = true))

            val result = useCase(command(breedingResultId))

            assert(
                result.getError() ==
                    RegisterFoalUseCaseError.BreedingResultNotFound(breedingResultId)
            )
        }

        @Test
        fun `繁殖登録が見つからないと BreedingRegistrationNotFound を返し永続化されない`() {
            val w = Wiring()
            every { w.breedingRegistrationRepository.findById(w.breedingRegistration.id) } returns
                null

            val result = w.useCase()(command(w.breedingResult.id.value))

            assert(
                result.getError() ==
                    RegisterFoalUseCaseError.BreedingRegistrationNotFound(
                        w.breedingRegistration.id.value
                    )
            )
            verify(exactly = 0) { w.bloodHorseRepository.save(any()) }
        }

        @Test
        fun `父が見つからないと SireNotFound を返し永続化されない`() {
            val w = Wiring()
            every { w.bloodHorseRepository.findById(w.sire.id) } returns null

            val result = w.useCase()(command(w.breedingResult.id.value))

            assert(result.getError() == RegisterFoalUseCaseError.SireNotFound(w.sire.id.value))
            verify(exactly = 0) { w.bloodHorseRepository.save(any()) }
        }

        @Test
        fun `母が見つからないと DamNotFound を返し永続化されない`() {
            val w = Wiring()
            every { w.bloodHorseRepository.findById(w.dam.id) } returns null

            val result = w.useCase()(command(w.breedingResult.id.value))

            assert(result.getError() == RegisterFoalUseCaseError.DamNotFound(w.dam.id.value))
            verify(exactly = 0) { w.bloodHorseRepository.save(any()) }
        }
    }

    @Nested
    inner class PreconditionFailureCase {
        @Test
        fun `分娩結果が未報告だと PreconditionViolated(NotLiveFoal) を返す`() {
            val w = Wiring()
            val notReported =
                BreedingFixture.breedingResult(
                    broodmareRegistration = w.breedingRegistration,
                    stallionRegistration = BreedingFixture.stallionRegistration(stallion = w.sire),
                )
            every { w.breedingResultRepository.findById(notReported.id) } returns notReported

            val result = w.useCase()(command(notReported.id.value))

            assert(
                result.getError() ==
                    RegisterFoalUseCaseError.PreconditionViolated(
                        RegisterFoalError.NotLiveFoal(null)
                    )
            )
            verify(exactly = 0) { w.bloodHorseRepository.save(any()) }
        }

        @Test
        fun `種付せずの繁殖成績だと PreconditionViolated(NotLiveFoal) を返し永続化されない`() {
            val w = Wiring()
            val uncovered =
                BreedingFixture.uncoveredBreedingResult(
                    broodmareRegistration = w.breedingRegistration
                )
            every { w.breedingResultRepository.findById(uncovered.id) } returns uncovered

            val result = w.useCase()(command(uncovered.id.value))

            assert(
                result.getError() ==
                    RegisterFoalUseCaseError.PreconditionViolated(
                        RegisterFoalError.NotLiveFoal(FoalingOutcome.NotCovered)
                    )
            )
            verify(exactly = 0) { w.bloodHorseRepository.save(any()) }
        }

        @Test
        fun `委譲先の前提条件違反は PreconditionViolated(RegistrationFailed) に wrap される`() {
            // 父が雌のため registerInStudBook が SireNotMale を返す
            val w = Wiring()
            val female = BloodHorseFixture.bloodHorse(sex = Sex.FEMALE)
            every { w.bloodHorseRepository.findById(w.sire.id) } returns female

            val result = w.useCase()(command(w.breedingResult.id.value))

            assert(
                result.getError() ==
                    RegisterFoalUseCaseError.PreconditionViolated(
                        RegisterFoalError.RegistrationFailed(RegisterInStudBookError.SireNotMale)
                    )
            )
            verify(exactly = 0) { w.bloodHorseRepository.save(any()) }
        }
    }
}
