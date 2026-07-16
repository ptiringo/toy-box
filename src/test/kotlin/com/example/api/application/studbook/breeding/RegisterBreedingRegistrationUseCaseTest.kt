package com.example.api.application.studbook.breeding

import com.example.api.domain.shared.AccountId
import com.example.api.domain.shared.Actor
import com.example.api.domain.shared.Command
import com.example.api.domain.studbook.model.StudbookPermissions
import com.example.api.domain.studbook.model.breeding.BreedingRegistrationRepository
import com.example.api.domain.studbook.model.breeding.BreedingRole
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseFixture
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseId
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseRepository
import com.example.api.domain.studbook.model.horse.bloodhorse.Sex
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class RegisterBreedingRegistrationUseCaseTest {

    private val actor =
        Actor(
            AccountId(UUID.randomUUID()),
            setOf(StudbookPermissions.BREEDING_REGISTRATION_REGISTER),
        )

    private fun command(
        payload: RegisterBreedingRegistrationCommand
    ): Command<RegisterBreedingRegistrationCommand> = Command(payload, Instant.now())

    @Nested
    inner class SuccessCase {
        @Test
        fun `雌馬を繁殖登録すると繁殖牝馬ロールの繁殖登録が永続化される`() {
            val mare = BloodHorseFixture.bloodHorse(sex = Sex.FEMALE)
            val bloodHorseRepository =
                mockk<BloodHorseRepository> { every { findById(mare.id) } returns mare }
            val breedingRegistrationRepository =
                mockk<BreedingRegistrationRepository> {
                    every { existsByRegistrationNumber(any()) } returns false
                    every { save(any()) } answers { Ok(firstArg()) }
                }
            val useCase =
                RegisterBreedingRegistrationUseCase(
                    bloodHorseRepository,
                    breedingRegistrationRepository,
                )

            val result =
                useCase(
                        actor,
                        command(RegisterBreedingRegistrationCommand(mare.id.value, "B-2024-0001")),
                    )
                    .unwrap()

            assert(result.registeredHorseId == mare.id)
            assert(result.role == BreedingRole.BROODMARE)
            assert(result.registrationNumber.value == "B-2024-0001")
            assert(!result.isRetired)
            verify(exactly = 1) { breedingRegistrationRepository.save(any()) }
        }

        @Test
        fun `雄馬を繁殖登録すると種牡馬ロールになる`() {
            val stallion = BloodHorseFixture.bloodHorse(sex = Sex.MALE)
            val bloodHorseRepository =
                mockk<BloodHorseRepository> { every { findById(stallion.id) } returns stallion }
            val breedingRegistrationRepository =
                mockk<BreedingRegistrationRepository> {
                    every { existsByRegistrationNumber(any()) } returns false
                    every { save(any()) } answers { Ok(firstArg()) }
                }
            val useCase =
                RegisterBreedingRegistrationUseCase(
                    bloodHorseRepository,
                    breedingRegistrationRepository,
                )

            val result =
                useCase(
                        actor,
                        command(
                            RegisterBreedingRegistrationCommand(stallion.id.value, "B-2024-0002")
                        ),
                    )
                    .unwrap()

            assert(result.role == BreedingRole.STALLION)
        }
    }

    @Nested
    inner class FailureCase {
        @Test
        fun `繁殖登録番号がブランクのとき InvalidRegistrationNumber を返し永続化されない`() {
            val mare = BloodHorseFixture.bloodHorse(sex = Sex.FEMALE)
            val bloodHorseRepository = mockk<BloodHorseRepository>()
            val breedingRegistrationRepository = mockk<BreedingRegistrationRepository>()
            val useCase =
                RegisterBreedingRegistrationUseCase(
                    bloodHorseRepository,
                    breedingRegistrationRepository,
                )

            val result =
                useCase(actor, command(RegisterBreedingRegistrationCommand(mare.id.value, "   ")))

            assert(
                result.getError() ==
                    RegisterBreedingRegistrationUseCaseError.InvalidRegistrationNumber
            )
            verify(exactly = 0) { breedingRegistrationRepository.save(any()) }
        }

        @Test
        fun `対象の軽種馬が存在しないとき HorseNotFound を返し永続化されない`() {
            val bloodHorseId = UUID.randomUUID()
            val bloodHorseRepository =
                mockk<BloodHorseRepository> {
                    every { findById(BloodHorseId(bloodHorseId)) } returns null
                }
            val breedingRegistrationRepository =
                mockk<BreedingRegistrationRepository> {
                    every { existsByRegistrationNumber(any()) } returns false
                }
            val useCase =
                RegisterBreedingRegistrationUseCase(
                    bloodHorseRepository,
                    breedingRegistrationRepository,
                )

            val result =
                useCase(
                    actor,
                    command(RegisterBreedingRegistrationCommand(bloodHorseId, "B-2024-0001")),
                )

            assert(
                result.getError() ==
                    RegisterBreedingRegistrationUseCaseError.HorseNotFound(bloodHorseId)
            )
            verify(exactly = 0) { breedingRegistrationRepository.save(any()) }
        }

        @Test
        fun `権限を持たない Actor で呼ぶと Forbidden を返し引き当ても永続化もしない`() {
            val bloodHorseRepository = mockk<BloodHorseRepository>()
            val breedingRegistrationRepository = mockk<BreedingRegistrationRepository>()
            val useCase =
                RegisterBreedingRegistrationUseCase(
                    bloodHorseRepository,
                    breedingRegistrationRepository,
                )
            val noPermissionActor = Actor(AccountId(UUID.randomUUID()), emptySet())

            val result =
                useCase(
                    noPermissionActor,
                    command(RegisterBreedingRegistrationCommand(UUID.randomUUID(), "B-2024-0001")),
                )

            assert(
                result.getError() ==
                    RegisterBreedingRegistrationUseCaseError.Forbidden(
                        StudbookPermissions.BREEDING_REGISTRATION_REGISTER
                    )
            )
            verify(exactly = 0) { bloodHorseRepository.findById(any()) }
            verify(exactly = 0) { breedingRegistrationRepository.save(any()) }
        }

        @Test
        fun `繁殖登録番号が既に使用済みのとき RegistrationNumberAlreadyTaken を返し永続化されない`() {
            val breedingRegistrationRepository =
                mockk<BreedingRegistrationRepository> {
                    every { existsByRegistrationNumber(any()) } returns true
                }
            val bloodHorseRepository = mockk<BloodHorseRepository>()
            val useCase =
                RegisterBreedingRegistrationUseCase(
                    bloodHorseRepository,
                    breedingRegistrationRepository,
                )

            val result =
                useCase(
                    actor,
                    command(RegisterBreedingRegistrationCommand(UUID.randomUUID(), "B-2024-0001")),
                )

            assert(
                result.getError() ==
                    RegisterBreedingRegistrationUseCaseError.RegistrationNumberAlreadyTaken(
                        "B-2024-0001"
                    )
            )
            verify(exactly = 0) { bloodHorseRepository.findById(any()) }
            verify(exactly = 0) { breedingRegistrationRepository.save(any()) }
        }
    }
}
