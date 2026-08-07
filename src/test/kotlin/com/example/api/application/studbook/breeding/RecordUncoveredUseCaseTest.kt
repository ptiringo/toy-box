package com.example.api.application.studbook.breeding

import com.example.api.domain.shared.AccountId
import com.example.api.domain.shared.Actor
import com.example.api.domain.shared.Command
import com.example.api.domain.shared.WorldId
import com.example.api.domain.shared.generateId
import com.example.api.domain.studbook.model.breeding.BreedingFixture
import com.example.api.domain.studbook.model.breeding.BreedingRegistrationId
import com.example.api.domain.studbook.model.breeding.BreedingRegistrationRepository
import com.example.api.domain.studbook.model.breeding.BreedingResultRepository
import com.example.api.domain.studbook.model.breeding.FoalingOutcome
import com.example.api.domain.studbook.model.breeding.RecordUncoveredError
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import java.time.Year
import java.util.UUID
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/** 世界スコープ（#704）のテスト用フィクスチャ。ネストしたテストクラスからも参照できるようファイル直下に置く。 */
private val worldId = WorldId(generateId())
private val actor = Actor(accountId = AccountId(generateId()), worldId = worldId)

class RecordUncoveredUseCaseTest {
    private fun command(payload: RecordUncoveredCommand): Command<RecordUncoveredCommand> =
        Command(payload, Instant.now())

    @Nested
    inner class SuccessCase {
        @Test
        fun `前提条件を満たすとき種付せずの終端な繁殖成績が永続化される`() {
            val broodmareRegistration = BreedingFixture.breedingRegistration()
            val registrationRepository =
                mockk<BreedingRegistrationRepository> {
                    every { findById(worldId, broodmareRegistration.id) } returns
                        broodmareRegistration
                }
            val breedingResultRepository =
                mockk<BreedingResultRepository> {
                    every {
                        findByBreedingRegistrationIdAndBreedingYear(worldId, any(), any())
                    } returns null
                    every { save(worldId, any()) } answers { Ok(secondArg()) }
                }
            val useCase = RecordUncoveredUseCase(registrationRepository, breedingResultRepository)

            val result =
                useCase(
                        actor,
                        command(
                            RecordUncoveredCommand(broodmareRegistration.id.value, Year.of(2024))
                        ),
                    )
                    .unwrap()

            assert(result.breedingRegistrationId == broodmareRegistration.id)
            assert(result.breedingYear == Year.of(2024))
            assert(result.covering == null)
            assert(result.outcome == FoalingOutcome.NotCovered)
            verify(exactly = 1) { breedingResultRepository.save(worldId, any()) }
        }
    }

    @Nested
    inner class FailureCase {
        @Test
        fun `繁殖登録が見つからないとき BreedingRegistrationNotFound を返し永続化されない`() {
            val breedingRegistrationId = UUID.randomUUID()
            val registrationRepository =
                mockk<BreedingRegistrationRepository> {
                    every {
                        findById(worldId, BreedingRegistrationId(breedingRegistrationId))
                    } returns null
                }
            val breedingResultRepository = mockk<BreedingResultRepository>()
            val useCase = RecordUncoveredUseCase(registrationRepository, breedingResultRepository)

            val result =
                useCase(
                    actor,
                    command(RecordUncoveredCommand(breedingRegistrationId, Year.of(2024))),
                )

            assert(
                result.getError() ==
                    RecordUncoveredUseCaseError.BreedingRegistrationNotFound(breedingRegistrationId)
            )
            verify(exactly = 0) { breedingResultRepository.save(worldId, any()) }
        }

        @Test
        fun `対象のロールが繁殖牝馬でないときドメイン検証違反を PreconditionViolated に wrap して返す`() {
            val stallionRegistration = BreedingFixture.stallionRegistration()
            val registrationRepository =
                mockk<BreedingRegistrationRepository> {
                    every { findById(worldId, stallionRegistration.id) } returns
                        stallionRegistration
                }
            val breedingResultRepository =
                mockk<BreedingResultRepository> {
                    every {
                        findByBreedingRegistrationIdAndBreedingYear(worldId, any(), any())
                    } returns null
                }
            val useCase = RecordUncoveredUseCase(registrationRepository, breedingResultRepository)

            val result =
                useCase(
                    actor,
                    command(RecordUncoveredCommand(stallionRegistration.id.value, Year.of(2024))),
                )

            assert(
                result.getError() ==
                    RecordUncoveredUseCaseError.PreconditionViolated(
                        RecordUncoveredError.NotBroodmare
                    )
            )
            verify(exactly = 0) { breedingResultRepository.save(worldId, any()) }
        }

        @Test
        fun `同一繁殖牝馬の同一繁殖年に既存成績があると AlreadyRecordedForYear を PreconditionViolated に wrap して返す`() {
            val broodmareRegistration = BreedingFixture.breedingRegistration()
            val existing =
                BreedingFixture.uncoveredBreedingResult(
                    broodmareRegistration = broodmareRegistration,
                    breedingYear = Year.of(2024),
                )
            val registrationRepository =
                mockk<BreedingRegistrationRepository> {
                    every { findById(worldId, broodmareRegistration.id) } returns
                        broodmareRegistration
                }
            val breedingResultRepository =
                mockk<BreedingResultRepository> {
                    every {
                        findByBreedingRegistrationIdAndBreedingYear(
                            worldId,
                            broodmareRegistration.id,
                            Year.of(2024),
                        )
                    } returns existing
                }
            val useCase = RecordUncoveredUseCase(registrationRepository, breedingResultRepository)

            val result =
                useCase(
                    actor,
                    command(RecordUncoveredCommand(broodmareRegistration.id.value, Year.of(2024))),
                )

            assert(
                result.getError() ==
                    RecordUncoveredUseCaseError.PreconditionViolated(
                        RecordUncoveredError.AlreadyRecordedForYear(Year.of(2024), existing.id)
                    )
            )
            verify(exactly = 0) { breedingResultRepository.save(worldId, any()) }
        }
    }
}
