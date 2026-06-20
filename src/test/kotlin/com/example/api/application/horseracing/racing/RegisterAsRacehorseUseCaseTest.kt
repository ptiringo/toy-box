package com.example.api.application.horseracing.racing

import com.example.api.domain.horseracing.model.horse.bloodhorse.BloodHorseFixture
import com.example.api.domain.horseracing.model.horse.bloodhorse.BloodHorseId
import com.example.api.domain.horseracing.model.horse.bloodhorse.BloodHorseRepository
import com.example.api.domain.horseracing.model.horse.bloodhorse.HorseName
import com.example.api.domain.horseracing.model.racing.RacingRegistrationRepository
import com.example.api.domain.horseracing.service.racing.RegisterAsRacehorseError
import com.example.api.domain.shared.Command
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class RegisterAsRacehorseUseCaseTest {

    private fun validPayload(bloodHorseId: UUID) =
        RegisterAsRacehorseCommand(bloodHorseId = bloodHorseId, registrationNumber = "R2024001")

    private fun command(payload: RegisterAsRacehorseCommand): Command<RegisterAsRacehorseCommand> =
        Command(payload, Instant.now())

    private fun namedHorse() =
        BloodHorseFixture.bloodHorse().assignName(HorseName.create("ナリタブライアン").unwrap()).unwrap()

    @Nested
    inner class SuccessCase {
        @Test
        fun `馬名登録済みの馬を競走馬登録すると対象馬を ID 参照する登録が永続化される`() {
            val horse = namedHorse()
            val bloodHorseRepository =
                mockk<BloodHorseRepository> { every { findById(horse.id) } returns horse }
            val racingRegistrationRepository =
                mockk<RacingRegistrationRepository> { every { save(any()) } answers { firstArg() } }
            val useCase =
                RegisterAsRacehorseUseCase(bloodHorseRepository, racingRegistrationRepository)

            val registration = useCase(command(validPayload(horse.id.value))).unwrap()

            assert(registration.racehorseId == horse.id)
            assert(registration.registrationNumber.value == "R2024001")
            verify(exactly = 1) { racingRegistrationRepository.save(any()) }
        }
    }

    @Nested
    inner class FailureCase {
        @Test
        fun `競走馬登録番号がブランクのとき InvalidRegistrationNumber を返し永続化されない`() {
            val bloodHorseRepository = mockk<BloodHorseRepository>()
            val racingRegistrationRepository = mockk<RacingRegistrationRepository>()
            val useCase =
                RegisterAsRacehorseUseCase(bloodHorseRepository, racingRegistrationRepository)

            val result =
                useCase(command(validPayload(UUID.randomUUID()).copy(registrationNumber = "")))

            assert(result.getError() == RegisterAsRacehorseUseCaseError.InvalidRegistrationNumber)
            verify(exactly = 0) { racingRegistrationRepository.save(any()) }
        }

        @Test
        fun `対象馬が見つからないとき HorseNotFound を返し永続化されない`() {
            val bloodHorseId = UUID.randomUUID()
            val bloodHorseRepository =
                mockk<BloodHorseRepository> {
                    every { findById(BloodHorseId(bloodHorseId)) } returns null
                }
            val racingRegistrationRepository = mockk<RacingRegistrationRepository>()
            val useCase =
                RegisterAsRacehorseUseCase(bloodHorseRepository, racingRegistrationRepository)

            val result = useCase(command(validPayload(bloodHorseId)))

            assert(result.getError() == RegisterAsRacehorseUseCaseError.HorseNotFound(bloodHorseId))
            verify(exactly = 0) { racingRegistrationRepository.save(any()) }
        }

        @Test
        fun `未命名の馬のときドメイン検証違反を PreconditionViolated に wrap して返す`() {
            val horse = BloodHorseFixture.bloodHorse()
            val bloodHorseRepository =
                mockk<BloodHorseRepository> { every { findById(horse.id) } returns horse }
            val racingRegistrationRepository = mockk<RacingRegistrationRepository>()
            val useCase =
                RegisterAsRacehorseUseCase(bloodHorseRepository, racingRegistrationRepository)

            val result = useCase(command(validPayload(horse.id.value)))

            assert(
                result.getError() ==
                    RegisterAsRacehorseUseCaseError.PreconditionViolated(
                        RegisterAsRacehorseError.NotNamed
                    )
            )
            verify(exactly = 0) { racingRegistrationRepository.save(any()) }
        }
    }
}
