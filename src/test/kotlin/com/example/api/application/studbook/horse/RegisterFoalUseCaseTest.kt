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
import com.example.api.domain.studbook.service.horse.RegisterFoalError
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
                every { findById(worldId, breedingResult.id) } returns breedingResult
            }
        val breedingRegistrationRepository =
            mockk<BreedingRegistrationRepository> {
                every { findById(worldId, breedingRegistration.id) } returns breedingRegistration
            }
        val bloodHorseRepository =
            mockk<BloodHorseRepository> {
                every { existsByRegistrationNumber(worldId, any()) } returns false
                every { findById(worldId, sire.id) } returns sire
                every { findById(worldId, dam.id) } returns dam
                every { save(worldId, any()) } answers { Ok(secondArg()) }
            }
        val horseInspectionRepository =
            mockk<HorseInspectionRepository> {
                every { save(worldId, any()) } answers { secondArg() }
            }

        fun useCase() =
            RegisterFoalUseCase(
                breedingResultRepository,
                breedingRegistrationRepository,
                bloodHorseRepository,
                horseInspectionRepository,
            )
    }

    @Nested
    inner class SuccessCase {
        @Test
        fun `産駒が血統登録され父母が繁殖記録から解決され出生日が分娩日になる`() {
            val w = Wiring()

            val registered = w.useCase()(actor, command(w.breedingResult.id.value)).unwrap()

            val bloodHorse = registered.bloodHorse
            assert(bloodHorse.origin == Origin.Domestic(sireId = w.sire.id, damId = w.dam.id))
            assert(bloodHorse.dateOfBirth.value == foalingDate)
            assert(bloodHorse.inspectionId == registered.inspection.id)
            verify(exactly = 1) { w.bloodHorseRepository.save(worldId, any()) }
        }
    }

    @Nested
    inner class ValidationFailureCase {
        @Test
        fun `血統登録番号がブランクだと InvalidRegistrationNumber を返す`() {
            val w = Wiring()

            val result =
                w.useCase()(actor, command(w.breedingResult.id.value, registrationNumber = ""))

            assert(result.getError() == RegisterFoalUseCaseError.InvalidRegistrationNumber)
            verify(exactly = 0) { w.bloodHorseRepository.save(worldId, any()) }
        }

        @Test
        fun `血統登録番号が既に採番済みだと RegistrationNumberAlreadyTaken を返す`() {
            val w = Wiring()
            every { w.bloodHorseRepository.existsByRegistrationNumber(worldId, any()) } returns true

            val result =
                w.useCase()(
                    actor,
                    command(w.breedingResult.id.value, registrationNumber = "2024104567"),
                )

            assert(
                result.getError() ==
                    RegisterFoalUseCaseError.RegistrationNumberAlreadyTaken("2024104567")
            )
            verify(exactly = 0) { w.bloodHorseRepository.save(worldId, any()) }
        }

        @Test
        fun `マイクロチップ番号が不正だと InvalidMicrochipNumber を返す`() {
            val w = Wiring()

            val result =
                w.useCase()(actor, command(w.breedingResult.id.value, microchipNumber = "123"))

            assert(result.getError() == RegisterFoalUseCaseError.InvalidMicrochipNumber)
            verify(exactly = 0) { w.bloodHorseRepository.save(worldId, any()) }
        }

        @Test
        fun `生産者名がブランクだと BlankBreeder を返す`() {
            val w = Wiring()

            val result = w.useCase()(actor, command(w.breedingResult.id.value, breeder = ""))

            assert(result.getError() == RegisterFoalUseCaseError.BlankBreeder)
            verify(exactly = 0) { w.bloodHorseRepository.save(worldId, any()) }
        }
    }

    @Nested
    inner class LookupFailureCase {
        @Test
        fun `繁殖成績が見つからないと BreedingResultNotFound を返し永続化されない`() {
            val breedingResultId = UUID.randomUUID()
            val repository =
                mockk<BreedingResultRepository> {
                    every { findById(worldId, BreedingResultId(breedingResultId)) } returns null
                }
            val useCase =
                RegisterFoalUseCase(
                    repository,
                    mockk(),
                    mockk(relaxed = true),
                    mockk(relaxed = true),
                )

            val result = useCase(actor, command(breedingResultId))

            assert(
                result.getError() ==
                    RegisterFoalUseCaseError.BreedingResultNotFound(breedingResultId)
            )
        }

        @Test
        fun `繁殖登録が見つからないと BreedingRegistrationNotFound を返し永続化されない`() {
            val w = Wiring()
            every {
                w.breedingRegistrationRepository.findById(worldId, w.breedingRegistration.id)
            } returns null

            val result = w.useCase()(actor, command(w.breedingResult.id.value))

            assert(
                result.getError() ==
                    RegisterFoalUseCaseError.BreedingRegistrationNotFound(
                        w.breedingRegistration.id.value
                    )
            )
            verify(exactly = 0) { w.bloodHorseRepository.save(worldId, any()) }
        }

        @Test
        fun `父が見つからないと SireNotFound を返し永続化されない`() {
            val w = Wiring()
            every { w.bloodHorseRepository.findById(worldId, w.sire.id) } returns null

            val result = w.useCase()(actor, command(w.breedingResult.id.value))

            assert(result.getError() == RegisterFoalUseCaseError.SireNotFound(w.sire.id.value))
            verify(exactly = 0) { w.bloodHorseRepository.save(worldId, any()) }
        }

        @Test
        fun `母が見つからないと DamNotFound を返し永続化されない`() {
            val w = Wiring()
            every { w.bloodHorseRepository.findById(worldId, w.dam.id) } returns null

            val result = w.useCase()(actor, command(w.breedingResult.id.value))

            assert(result.getError() == RegisterFoalUseCaseError.DamNotFound(w.dam.id.value))
            verify(exactly = 0) { w.bloodHorseRepository.save(worldId, any()) }
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
            every { w.breedingResultRepository.findById(worldId, notReported.id) } returns
                notReported

            val result = w.useCase()(actor, command(notReported.id.value))

            assert(
                result.getError() ==
                    RegisterFoalUseCaseError.PreconditionViolated(
                        RegisterFoalError.NotLiveFoal(null)
                    )
            )
            verify(exactly = 0) { w.bloodHorseRepository.save(worldId, any()) }
        }

        @Test
        fun `種付せずの繁殖成績だと PreconditionViolated(NotLiveFoal) を返し永続化されない`() {
            val w = Wiring()
            val uncovered =
                BreedingFixture.uncoveredBreedingResult(
                    broodmareRegistration = w.breedingRegistration
                )
            every { w.breedingResultRepository.findById(worldId, uncovered.id) } returns uncovered

            val result = w.useCase()(actor, command(uncovered.id.value))

            assert(
                result.getError() ==
                    RegisterFoalUseCaseError.PreconditionViolated(
                        RegisterFoalError.NotLiveFoal(FoalingOutcome.NotCovered)
                    )
            )
            verify(exactly = 0) { w.bloodHorseRepository.save(worldId, any()) }
        }

        @Test
        fun `委譲先の前提条件違反は PreconditionViolated(RegistrationFailed) に wrap される`() {
            // 父が雌のため registerInStudBook が SireNotMale を返す
            val w = Wiring()
            val female = BloodHorseFixture.bloodHorse(sex = Sex.FEMALE)
            every { w.bloodHorseRepository.findById(worldId, w.sire.id) } returns female

            val result = w.useCase()(actor, command(w.breedingResult.id.value))

            assert(
                result.getError() ==
                    RegisterFoalUseCaseError.PreconditionViolated(
                        RegisterFoalError.RegistrationFailed(RegisterInStudBookError.SireNotMale)
                    )
            )
            verify(exactly = 0) { w.bloodHorseRepository.save(worldId, any()) }
        }

        @Test
        fun `前提条件違反で登録が失敗したとき審査が保存されない`() {
            // 父が雌のため registerFoal が RegistrationFailed(SireNotMale) を返す → 審査 save に到達しない
            val w = Wiring()
            val female = BloodHorseFixture.bloodHorse(sex = Sex.FEMALE)
            every { w.bloodHorseRepository.findById(worldId, w.sire.id) } returns female
            // inspectionRepository は save を許可しないで生成（呼ばれると例外）
            val strictInspectionRepository = mockk<HorseInspectionRepository>()
            val useCase =
                RegisterFoalUseCase(
                    w.breedingResultRepository,
                    w.breedingRegistrationRepository,
                    w.bloodHorseRepository,
                    strictInspectionRepository,
                )

            useCase(actor, command(w.breedingResult.id.value))

            verify(exactly = 0) { strictInspectionRepository.save(worldId, any()) }
        }
    }
}
