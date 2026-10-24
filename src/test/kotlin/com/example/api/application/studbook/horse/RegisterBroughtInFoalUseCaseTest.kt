package com.example.api.application.studbook.horse

import com.example.api.domain.shared.AccountId
import com.example.api.domain.shared.Actor
import com.example.api.domain.shared.Command
import com.example.api.domain.shared.WorldId
import com.example.api.domain.shared.generateId
import com.example.api.domain.studbook.model.breeding.BreedingFixture
import com.example.api.domain.studbook.model.breeding.BreedingRegistration
import com.example.api.domain.studbook.model.breeding.BreedingRegistrationRepository
import com.example.api.domain.studbook.model.breeding.BreedingResult
import com.example.api.domain.studbook.model.breeding.BreedingResultId
import com.example.api.domain.studbook.model.breeding.BreedingResultRepository
import com.example.api.domain.studbook.model.breeding.FoalingOutcome
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorse
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseFixture
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseRepository
import com.example.api.domain.studbook.model.horse.bloodhorse.BreedType
import com.example.api.domain.studbook.model.horse.bloodhorse.CoatColor
import com.example.api.domain.studbook.model.horse.bloodhorse.Origin
import com.example.api.domain.studbook.model.horse.bloodhorse.RegisterInStudBookError
import com.example.api.domain.studbook.model.horse.bloodhorse.Sex
import com.example.api.domain.studbook.model.inspection.DnaParentageResult
import com.example.api.domain.studbook.model.inspection.HorseInspectionRepository
import com.example.api.domain.studbook.service.horse.RegisterBroughtInFoalError
import com.github.michaelbull.result.Ok
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

/** 世界スコープ（#704）のテスト用フィクスチャ。ネストしたテストクラスからも参照できるようファイル直下に置く。 */
private val worldId = WorldId(generateId())
private val actor = Actor(accountId = AccountId(generateId()), worldId = worldId)

class RegisterBroughtInFoalUseCaseTest {
    private val foalingDate = LocalDate.of(2000, 3, 21)

    private fun command(
        breedingResultId: UUID,
        breedType: BreedType = BreedType.THOROUGHBRED,
        microchipNumber: String = "392140000000001",
        breeder: String = "ノーザンファーム",
        registrationNumber: String = "2000104567",
    ): Command<RegisterBroughtInFoalCommand> =
        Command(
            RegisterBroughtInFoalCommand(
                breedingResultId = breedingResultId,
                sex = Sex.FEMALE,
                coatColor = CoatColor.BAY,
                breedType = breedType,
                breeder = breeder,
                microchipNumber = microchipNumber,
                dnaParentage = DnaParentageResult.CONSISTENT,
                registrationNumber = registrationNumber,
            ),
            Instant.now(),
        )

    /** 母・繁殖登録・海外種付の繁殖成績（生産済み）が解決できる正常系の土台を組む。 */
    private inner class Wiring {
        val dam: BloodHorse = BloodHorseFixture.bloodHorse(sex = Sex.FEMALE)
        val breedingRegistration: BreedingRegistration =
            BreedingFixture.breedingRegistration(broodmare = dam)
        val breedingResult: BreedingResult =
            BreedingFixture.overseasBreedingResult(broodmareRegistration = breedingRegistration)
                .recordFoaling(FoalingOutcome.LiveFoal(foalingDate))
                .unwrap()

        val breedingResultRepository =
            mockk<BreedingResultRepository> {
                every { findById(worldId, breedingResult.id) } returns breedingResult
            }
        val breedingRegistrationRepository =
            mockk<BreedingRegistrationRepository> {
                every { findById(worldId, breedingRegistration.id) } returns breedingRegistration
            }
        val bloodHorseRepository =
            mockk<BloodHorseRepository> {
                every { existsByRegistrationNumber(worldId, any()) } returns false
                every { findById(worldId, dam.id) } returns dam
                every { save(worldId, any()) } answers { Ok(secondArg()) }
            }
        val horseInspectionRepository =
            mockk<HorseInspectionRepository> {
                every { save(worldId, any()) } answers { secondArg() }
            }

        fun useCase() =
            RegisterBroughtInFoalUseCase(
                breedingResultRepository,
                breedingRegistrationRepository,
                bloodHorseRepository,
                horseInspectionRepository,
            )

        fun assertNothingSaved() {
            verify(exactly = 0) { bloodHorseRepository.save(worldId, any()) }
            verify(exactly = 0) { horseInspectionRepository.save(worldId, any()) }
        }
    }

    @Nested
    inner class SuccessCase {
        @Test
        fun `持込馬が血統登録され、父が外国種牡馬・母が繁殖牝馬・出生日が分娩日になる`() {
            val w = Wiring()

            val registered = w.useCase()(actor, command(w.breedingResult.id.value)).unwrap()

            val bloodHorse = registered.bloodHorse
            assert(
                bloodHorse.origin ==
                    Origin.BroughtIn(sire = BloodHorseFixture.foreignSire(), damId = w.dam.id)
            )
            assert(bloodHorse.dateOfBirth.value == foalingDate)
            assert(bloodHorse.inspectionId == registered.inspection.id)
            verify(exactly = 1) { w.bloodHorseRepository.save(worldId, any()) }
            verify(exactly = 1) { w.horseInspectionRepository.save(worldId, any()) }
        }
    }

    @Nested
    inner class ValidationFailureCase {
        @Test
        fun `血統登録番号がブランクだと InvalidRegistrationNumber を返す`() {
            val w = Wiring()

            val result =
                w.useCase()(actor, command(w.breedingResult.id.value, registrationNumber = ""))

            assert(result.getError() == RegisterBroughtInFoalUseCaseError.InvalidRegistrationNumber)
            w.assertNothingSaved()
        }

        @Test
        fun `血統登録番号が既に採番済みだと RegistrationNumberAlreadyTaken を返す`() {
            val w = Wiring()
            every { w.bloodHorseRepository.existsByRegistrationNumber(worldId, any()) } returns true

            val result = w.useCase()(actor, command(w.breedingResult.id.value))

            assert(
                result.getError() ==
                    RegisterBroughtInFoalUseCaseError.RegistrationNumberAlreadyTaken("2000104567")
            )
            w.assertNothingSaved()
        }

        @Test
        fun `マイクロチップ番号が不正だと InvalidMicrochipNumber を返す`() {
            val w = Wiring()

            val result =
                w.useCase()(actor, command(w.breedingResult.id.value, microchipNumber = "123"))

            assert(result.getError() == RegisterBroughtInFoalUseCaseError.InvalidMicrochipNumber)
            w.assertNothingSaved()
        }

        @Test
        fun `生産者名がブランクだと BlankBreeder を返す`() {
            val w = Wiring()

            val result = w.useCase()(actor, command(w.breedingResult.id.value, breeder = " "))

            assert(result.getError() == RegisterBroughtInFoalUseCaseError.BlankBreeder)
            w.assertNothingSaved()
        }
    }

    @Nested
    inner class ReferenceFailureCase {
        @Test
        fun `繁殖成績が見つからないと BreedingResultNotFound を返す`() {
            val w = Wiring()
            val missing = generateId()
            every {
                w.breedingResultRepository.findById(worldId, BreedingResultId(missing))
            } returns null

            val result = w.useCase()(actor, command(missing))

            assert(
                result.getError() ==
                    RegisterBroughtInFoalUseCaseError.BreedingResultNotFound(missing)
            )
            w.assertNothingSaved()
        }

        @Test
        fun `繁殖登録が見つからないと BreedingRegistrationNotFound を返す`() {
            val w = Wiring()
            every {
                w.breedingRegistrationRepository.findById(worldId, w.breedingRegistration.id)
            } returns null

            val result = w.useCase()(actor, command(w.breedingResult.id.value))

            assert(
                result.getError() ==
                    RegisterBroughtInFoalUseCaseError.BreedingRegistrationNotFound(
                        w.breedingRegistration.id.value
                    )
            )
            w.assertNothingSaved()
        }

        @Test
        fun `母が見つからないと DamNotFound を返す`() {
            val w = Wiring()
            every { w.bloodHorseRepository.findById(worldId, w.dam.id) } returns null

            val result = w.useCase()(actor, command(w.breedingResult.id.value))

            assert(
                result.getError() == RegisterBroughtInFoalUseCaseError.DamNotFound(w.dam.id.value)
            )
            w.assertNothingSaved()
        }
    }

    @Nested
    inner class PreconditionFailureCase {
        @Test
        fun `国内種付の繁殖成績だと PreconditionViolated(NotOverseasCovering) を返す`() {
            val w = Wiring()
            val domestic =
                BreedingFixture.breedingResult(broodmareRegistration = w.breedingRegistration)
                    .recordFoaling(FoalingOutcome.LiveFoal(foalingDate))
                    .unwrap()
            every { w.breedingResultRepository.findById(worldId, domestic.id) } returns domestic

            val result = w.useCase()(actor, command(domestic.id.value))

            assert(
                result.getError() ==
                    RegisterBroughtInFoalUseCaseError.PreconditionViolated(
                        RegisterBroughtInFoalError.NotOverseasCovering(domestic.covering)
                    )
            )
            w.assertNothingSaved()
        }

        @Test
        fun `委譲先の前提条件違反は PreconditionViolated(RegistrationFailed) に wrap され審査も保存されない`() {
            val w = Wiring()

            val result =
                w.useCase()(
                    actor,
                    command(w.breedingResult.id.value, breedType = BreedType.ARAB),
                )

            assert(
                result.getError() ==
                    RegisterBroughtInFoalUseCaseError.PreconditionViolated(
                        RegisterBroughtInFoalError.RegistrationFailed(
                            RegisterInStudBookError.BreedMismatch
                        )
                    )
            )
            w.assertNothingSaved()
        }
    }
}
