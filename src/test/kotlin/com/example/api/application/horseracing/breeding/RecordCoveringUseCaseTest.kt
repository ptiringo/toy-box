package com.example.api.application.horseracing.breeding

import com.example.api.domain.horseracing.model.breeding.BreedingFixture
import com.example.api.domain.horseracing.model.breeding.BreedingRegistrationId
import com.example.api.domain.horseracing.model.breeding.BreedingRegistrationRepository
import com.example.api.domain.horseracing.model.breeding.BreedingResultRepository
import com.example.api.domain.horseracing.model.breeding.RecordCoveringError
import com.example.api.domain.horseracing.model.horse.bloodhorse.BloodHorseFixture
import com.example.api.domain.horseracing.model.horse.bloodhorse.BloodHorseId
import com.example.api.domain.horseracing.model.horse.bloodhorse.BloodHorseRepository
import com.example.api.domain.horseracing.model.horse.bloodhorse.Sex
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

class RecordCoveringUseCaseTest {

    /** すべて正しい既定のペイロード。変種は `copy` で 1 項目だけ差し替える。 */
    private fun validPayload(breedingRegistrationId: UUID, stallionId: UUID) =
        RecordCoveringCommand(
            breedingRegistrationId = breedingRegistrationId,
            stallionId = stallionId,
            coveringDate = LocalDate.of(2024, 4, 1),
            certificateNumber = "C-2024-0001",
        )

    private fun command(payload: RecordCoveringCommand): Command<RecordCoveringCommand> =
        Command(payload, Instant.now())

    @Nested
    inner class SuccessCase {
        @Test
        fun `前提条件を満たすとき種付が記録され分娩結果未報告の繁殖成績が永続化される`() {
            val registration = BreedingFixture.breedingRegistration()
            val stallion = BloodHorseFixture.bloodHorse(sex = Sex.MALE)
            val registrationRepository =
                mockk<BreedingRegistrationRepository> {
                    every { findById(registration.id) } returns registration
                }
            val bloodHorseRepository =
                mockk<BloodHorseRepository> { every { findById(stallion.id) } returns stallion }
            val breedingResultRepository =
                mockk<BreedingResultRepository> { every { save(any()) } answers { firstArg() } }
            val useCase =
                RecordCoveringUseCase(
                    registrationRepository,
                    bloodHorseRepository,
                    breedingResultRepository,
                )

            val result =
                useCase(command(validPayload(registration.id.value, stallion.id.value))).unwrap()

            assert(result.breedingRegistrationId == registration.id)
            assert(result.covering.stallionId == stallion.id)
            assert(result.covering.coveringDate == LocalDate.of(2024, 4, 1))
            assert(result.covering.certificateNumber.value == "C-2024-0001")
            assert(result.outcome == null)
            verify(exactly = 1) { breedingResultRepository.save(any()) }
        }
    }

    @Nested
    inner class FailureCase {
        @Test
        fun `種付証明書番号がブランクのとき InvalidCertificateNumber を返し永続化されない`() {
            val registrationRepository = mockk<BreedingRegistrationRepository>()
            val bloodHorseRepository = mockk<BloodHorseRepository>()
            val breedingResultRepository = mockk<BreedingResultRepository>()
            val useCase =
                RecordCoveringUseCase(
                    registrationRepository,
                    bloodHorseRepository,
                    breedingResultRepository,
                )

            val result =
                useCase(
                    command(
                        validPayload(UUID.randomUUID(), UUID.randomUUID())
                            .copy(certificateNumber = "")
                    )
                )

            assert(result.getError() == RecordCoveringUseCaseError.InvalidCertificateNumber)
            verify(exactly = 0) { breedingResultRepository.save(any()) }
        }

        @Test
        fun `繁殖登録が見つからないとき BreedingRegistrationNotFound を返し永続化されない`() {
            val breedingRegistrationId = UUID.randomUUID()
            val registrationRepository =
                mockk<BreedingRegistrationRepository> {
                    every { findById(BreedingRegistrationId(breedingRegistrationId)) } returns null
                }
            val bloodHorseRepository = mockk<BloodHorseRepository>()
            val breedingResultRepository = mockk<BreedingResultRepository>()
            val useCase =
                RecordCoveringUseCase(
                    registrationRepository,
                    bloodHorseRepository,
                    breedingResultRepository,
                )

            val result = useCase(command(validPayload(breedingRegistrationId, UUID.randomUUID())))

            assert(
                result.getError() ==
                    RecordCoveringUseCaseError.BreedingRegistrationNotFound(breedingRegistrationId)
            )
            verify(exactly = 0) { breedingResultRepository.save(any()) }
        }

        @Test
        fun `種牡馬が見つからないとき StallionNotFound を返し永続化されない`() {
            val registration = BreedingFixture.breedingRegistration()
            val stallionId = UUID.randomUUID()
            val registrationRepository =
                mockk<BreedingRegistrationRepository> {
                    every { findById(registration.id) } returns registration
                }
            val bloodHorseRepository =
                mockk<BloodHorseRepository> {
                    every { findById(BloodHorseId(stallionId)) } returns null
                }
            val breedingResultRepository = mockk<BreedingResultRepository>()
            val useCase =
                RecordCoveringUseCase(
                    registrationRepository,
                    bloodHorseRepository,
                    breedingResultRepository,
                )

            val result = useCase(command(validPayload(registration.id.value, stallionId)))

            assert(result.getError() == RecordCoveringUseCaseError.StallionNotFound(stallionId))
            verify(exactly = 0) { breedingResultRepository.save(any()) }
        }

        @Test
        fun `種牡馬が雄でないときドメイン検証違反を PreconditionViolated に wrap して返す`() {
            val registration = BreedingFixture.breedingRegistration()
            val stallion = BloodHorseFixture.bloodHorse(sex = Sex.FEMALE)
            val registrationRepository =
                mockk<BreedingRegistrationRepository> {
                    every { findById(registration.id) } returns registration
                }
            val bloodHorseRepository =
                mockk<BloodHorseRepository> { every { findById(stallion.id) } returns stallion }
            val breedingResultRepository = mockk<BreedingResultRepository>()
            val useCase =
                RecordCoveringUseCase(
                    registrationRepository,
                    bloodHorseRepository,
                    breedingResultRepository,
                )

            val result = useCase(command(validPayload(registration.id.value, stallion.id.value)))

            assert(
                result.getError() ==
                    RecordCoveringUseCaseError.PreconditionViolated(
                        RecordCoveringError.StallionNotMale
                    )
            )
            verify(exactly = 0) { breedingResultRepository.save(any()) }
        }
    }
}
