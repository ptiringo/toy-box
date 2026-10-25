package com.example.api.application.studbook.breeding

import com.example.api.domain.shared.AccountId
import com.example.api.domain.shared.Actor
import com.example.api.domain.shared.Command
import com.example.api.domain.shared.UpdateConflict
import com.example.api.domain.shared.WorldId
import com.example.api.domain.shared.generateId
import com.example.api.domain.studbook.model.breeding.BreedingFixture
import com.example.api.domain.studbook.model.breeding.BreedingRegistrationId
import com.example.api.domain.studbook.model.breeding.BreedingRegistrationRepository
import com.example.api.domain.studbook.model.breeding.BreedingRetirement
import com.example.api.domain.studbook.model.breeding.RetirementReason
import com.github.michaelbull.result.Err
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

class RetireBreedingRegistrationUseCaseTest {
    private val occurredOn = LocalDate.of(2025, 3, 1)

    private fun command(
        payload: RetireBreedingRegistrationCommand
    ): Command<RetireBreedingRegistrationCommand> = Command(payload, Instant.now())

    @Nested
    inner class SuccessCase {
        @Test
        fun `供用中の繁殖登録を供用停止すると事由と発生日が記録され楽観ロック付きで更新される`() {
            val registration = BreedingFixture.breedingRegistration()
            val repository =
                mockk<BreedingRegistrationRepository> {
                    every { findById(worldId, registration.id) } returns registration
                    every { save(worldId, any()) } answers { Ok(secondArg()) }
                }
            val useCase = RetireBreedingRegistrationUseCase(repository)

            val result =
                useCase(
                        actor,
                        command(
                            RetireBreedingRegistrationCommand(
                                registration.id.value,
                                RetirementReason.USE_CHANGE,
                                occurredOn,
                            )
                        ),
                    )
                    .unwrap()

            val expected = BreedingRetirement(RetirementReason.USE_CHANGE, occurredOn)
            assert(result.retirement == expected)
            assert(result.id == registration.id)
            // save には供用停止後の集約が渡ること
            verify(exactly = 1) { repository.save(worldId, match { it.retirement == expected }) }
        }
    }

    @Nested
    inner class FailureCase {
        @Test
        fun `対象の繁殖登録が見つからないとき BreedingRegistrationNotFound を返し永続化されない`() {
            val id = UUID.randomUUID()
            val repository =
                mockk<BreedingRegistrationRepository> {
                    every { findById(worldId, BreedingRegistrationId(id)) } returns null
                }
            val useCase = RetireBreedingRegistrationUseCase(repository)

            val result =
                useCase(
                    actor,
                    command(
                        RetireBreedingRegistrationCommand(id, RetirementReason.DEATH, occurredOn)
                    ),
                )

            assert(
                result.getError() ==
                    RetireBreedingRegistrationUseCaseError.BreedingRegistrationNotFound(id)
            )
            verify(exactly = 0) { repository.save(worldId, any()) }
        }

        @Test
        fun `既に供用停止済みの繁殖登録を再停止すると AlreadyRetired を返し永続化されない`() {
            val first = BreedingRetirement(RetirementReason.DEATH, occurredOn)
            val retired =
                BreedingFixture.breedingRegistration()
                    .retire(first.reason, first.occurredOn)
                    .unwrap()
            val repository =
                mockk<BreedingRegistrationRepository> {
                    every { findById(worldId, retired.id) } returns retired
                }
            val useCase = RetireBreedingRegistrationUseCase(repository)

            val result =
                useCase(
                    actor,
                    command(
                        RetireBreedingRegistrationCommand(
                            retired.id.value,
                            RetirementReason.OTHER,
                            LocalDate.of(2025, 4, 1),
                        )
                    ),
                )

            assert(
                result.getError() == RetireBreedingRegistrationUseCaseError.AlreadyRetired(first)
            )
            verify(exactly = 0) { repository.save(worldId, any()) }
        }

        @Test
        fun `更新が競合したとき ConcurrentModification を返す`() {
            val registration = BreedingFixture.breedingRegistration()
            val repository =
                mockk<BreedingRegistrationRepository> {
                    every { findById(worldId, registration.id) } returns registration
                    every { save(worldId, any()) } returns Err(UpdateConflict)
                }
            val useCase = RetireBreedingRegistrationUseCase(repository)

            val result =
                useCase(
                    actor,
                    command(
                        RetireBreedingRegistrationCommand(
                            registration.id.value,
                            RetirementReason.DEATH,
                            occurredOn,
                        )
                    ),
                )

            assert(
                result.getError() ==
                    RetireBreedingRegistrationUseCaseError.ConcurrentModification(
                        registration.id.value
                    )
            )
        }
    }
}
