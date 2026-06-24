package com.example.api.application.studbook.breeding

import com.example.api.domain.shared.Command
import com.example.api.domain.studbook.model.breeding.BreedingRegistration
import com.example.api.domain.studbook.model.breeding.BreedingRegistrationRepository
import com.example.api.domain.studbook.model.breeding.BreedingRole
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseFixture
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseId
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseRepository
import com.example.api.domain.studbook.model.horse.bloodhorse.Sex
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
            val registrationRepository =
                mockk<BreedingRegistrationRepository> {
                    every { save(any()) } answers { firstArg() }
                }
            val useCase =
                RegisterBreedingRegistrationUseCase(bloodHorseRepository, registrationRepository)

            val result =
                useCase(
                        command(
                            RegisterBreedingRegistrationCommand(
                                bloodHorseId = mare.id.value,
                                registrationNumber = "B-2024-0001",
                            )
                        )
                    )
                    .unwrap()

            assert(result.registeredHorseId == mare.id)
            assert(result.role == BreedingRole.BROODMARE)
            assert(result.registrationNumber.value == "B-2024-0001")
            assert(!result.isRetired)
            verify(exactly = 1) { registrationRepository.save(any()) }
        }

        @Test
        fun `雄馬を繁殖登録すると種牡馬ロールになる`() {
            val stallion = BloodHorseFixture.bloodHorse(sex = Sex.MALE)
            val bloodHorseRepository =
                mockk<BloodHorseRepository> { every { findById(stallion.id) } returns stallion }
            val registrationRepository =
                mockk<BreedingRegistrationRepository> {
                    every { save(any()) } answers { firstArg() }
                }
            val useCase =
                RegisterBreedingRegistrationUseCase(bloodHorseRepository, registrationRepository)

            val result =
                useCase(
                        command(
                            RegisterBreedingRegistrationCommand(
                                bloodHorseId = stallion.id.value,
                                registrationNumber = "B-2024-0002",
                            )
                        )
                    )
                    .unwrap()

            assert(result.role == BreedingRole.STALLION)
        }
    }

    @Nested
    inner class FailureCase {
        @Test
        fun `繁殖登録番号がブランクのとき BlankRegistrationNumber を返し永続化されない`() {
            val mare = BloodHorseFixture.bloodHorse(sex = Sex.FEMALE)
            val bloodHorseRepository = mockk<BloodHorseRepository>()
            val registrationRepository = mockk<BreedingRegistrationRepository>()
            val useCase =
                RegisterBreedingRegistrationUseCase(bloodHorseRepository, registrationRepository)

            val result =
                useCase(
                    command(
                        RegisterBreedingRegistrationCommand(
                            bloodHorseId = mare.id.value,
                            registrationNumber = "  ",
                        )
                    )
                )

            assert(
                result.getError() ==
                    RegisterBreedingRegistrationUseCaseError.BlankRegistrationNumber
            )
            verify(exactly = 0) { registrationRepository.save(any<BreedingRegistration>()) }
        }

        @Test
        fun `対象の軽種馬が見つからないとき BloodHorseNotFound を返し永続化されない`() {
            val bloodHorseId = UUID.randomUUID()
            val bloodHorseRepository =
                mockk<BloodHorseRepository> {
                    every { findById(BloodHorseId(bloodHorseId)) } returns null
                }
            val registrationRepository = mockk<BreedingRegistrationRepository>()
            val useCase =
                RegisterBreedingRegistrationUseCase(bloodHorseRepository, registrationRepository)

            val result =
                useCase(
                    command(
                        RegisterBreedingRegistrationCommand(
                            bloodHorseId = bloodHorseId,
                            registrationNumber = "B-2024-0001",
                        )
                    )
                )

            assert(
                result.getError() ==
                    RegisterBreedingRegistrationUseCaseError.BloodHorseNotFound(bloodHorseId)
            )
            verify(exactly = 0) { registrationRepository.save(any<BreedingRegistration>()) }
        }
    }
}
