package com.example.api.application.horseracing.breeding

import com.example.api.domain.horseracing.model.breeding.BreedingFixture
import com.example.api.domain.horseracing.model.breeding.BreedingRegistrationId
import com.example.api.domain.horseracing.model.breeding.BreedingRegistrationRepository
import com.example.api.domain.horseracing.model.breeding.BreedingResultRepository
import com.example.api.domain.horseracing.model.breeding.RecordCoveringError
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
    private fun validPayload(breedingRegistrationId: UUID, stallionRegistrationId: UUID) =
        RecordCoveringCommand(
            breedingRegistrationId = breedingRegistrationId,
            stallionRegistrationId = stallionRegistrationId,
            coveringDate = LocalDate.of(2024, 4, 1),
            certificateNumber = "C-2024-0001",
        )

    private fun command(payload: RecordCoveringCommand): Command<RecordCoveringCommand> =
        Command(payload, Instant.now())

    @Nested
    inner class SuccessCase {
        @Test
        fun `前提条件を満たすとき種付が記録され分娩結果未報告の繁殖成績が永続化される`() {
            val broodmareRegistration = BreedingFixture.breedingRegistration()
            val stallionRegistration = BreedingFixture.stallionRegistration()
            val registrationRepository =
                mockk<BreedingRegistrationRepository> {
                    every { findById(broodmareRegistration.id) } returns broodmareRegistration
                    every { findById(stallionRegistration.id) } returns stallionRegistration
                }
            val breedingResultRepository =
                mockk<BreedingResultRepository> { every { save(any()) } answers { firstArg() } }
            val useCase = RecordCoveringUseCase(registrationRepository, breedingResultRepository)

            val result =
                useCase(
                        command(
                            validPayload(
                                broodmareRegistration.id.value,
                                stallionRegistration.id.value,
                            )
                        )
                    )
                    .unwrap()

            assert(result.breedingRegistrationId == broodmareRegistration.id)
            assert(result.covering.stallionId == stallionRegistration.registeredHorseId)
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
            val breedingResultRepository = mockk<BreedingResultRepository>()
            val useCase = RecordCoveringUseCase(registrationRepository, breedingResultRepository)

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
        fun `繁殖牝馬の繁殖登録が見つからないとき BreedingRegistrationNotFound を返し永続化されない`() {
            val breedingRegistrationId = UUID.randomUUID()
            val registrationRepository =
                mockk<BreedingRegistrationRepository> {
                    every { findById(BreedingRegistrationId(breedingRegistrationId)) } returns null
                }
            val breedingResultRepository = mockk<BreedingResultRepository>()
            val useCase = RecordCoveringUseCase(registrationRepository, breedingResultRepository)

            val result = useCase(command(validPayload(breedingRegistrationId, UUID.randomUUID())))

            assert(
                result.getError() ==
                    RecordCoveringUseCaseError.BreedingRegistrationNotFound(breedingRegistrationId)
            )
            verify(exactly = 0) { breedingResultRepository.save(any()) }
        }

        @Test
        fun `種牡馬の繁殖登録が見つからないとき StallionRegistrationNotFound を返し永続化されない`() {
            val broodmareRegistration = BreedingFixture.breedingRegistration()
            val stallionRegistrationId = UUID.randomUUID()
            val registrationRepository =
                mockk<BreedingRegistrationRepository> {
                    every { findById(broodmareRegistration.id) } returns broodmareRegistration
                    every { findById(BreedingRegistrationId(stallionRegistrationId)) } returns null
                }
            val breedingResultRepository = mockk<BreedingResultRepository>()
            val useCase = RecordCoveringUseCase(registrationRepository, breedingResultRepository)

            val result =
                useCase(
                    command(validPayload(broodmareRegistration.id.value, stallionRegistrationId))
                )

            assert(
                result.getError() ==
                    RecordCoveringUseCaseError.StallionRegistrationNotFound(stallionRegistrationId)
            )
            verify(exactly = 0) { breedingResultRepository.save(any()) }
        }

        @Test
        fun `配合相手のロールが種牡馬でないときドメイン検証違反を PreconditionViolated に wrap して返す`() {
            val broodmareRegistration = BreedingFixture.breedingRegistration()
            val notStallionRegistration = BreedingFixture.breedingRegistration()
            val registrationRepository =
                mockk<BreedingRegistrationRepository> {
                    every { findById(broodmareRegistration.id) } returns broodmareRegistration
                    every { findById(notStallionRegistration.id) } returns notStallionRegistration
                }
            val breedingResultRepository = mockk<BreedingResultRepository>()
            val useCase = RecordCoveringUseCase(registrationRepository, breedingResultRepository)

            val result =
                useCase(
                    command(
                        validPayload(
                            broodmareRegistration.id.value,
                            notStallionRegistration.id.value,
                        )
                    )
                )

            assert(
                result.getError() ==
                    RecordCoveringUseCaseError.PreconditionViolated(RecordCoveringError.NotStallion)
            )
            verify(exactly = 0) { breedingResultRepository.save(any()) }
        }
    }
}
