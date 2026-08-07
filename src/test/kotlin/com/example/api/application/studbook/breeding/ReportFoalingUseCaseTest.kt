package com.example.api.application.studbook.breeding

import com.example.api.domain.shared.AccountId
import com.example.api.domain.shared.Actor
import com.example.api.domain.shared.Command
import com.example.api.domain.shared.UpdateConflict
import com.example.api.domain.shared.WorldId
import com.example.api.domain.shared.generateId
import com.example.api.domain.studbook.model.breeding.BreedingFixture
import com.example.api.domain.studbook.model.breeding.BreedingResultId
import com.example.api.domain.studbook.model.breeding.BreedingResultRepository
import com.example.api.domain.studbook.model.breeding.FoalingOutcome
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

class ReportFoalingUseCaseTest {
    private fun command(payload: ReportFoalingCommand): Command<ReportFoalingCommand> =
        Command(payload, Instant.now())

    @Nested
    inner class SuccessCase {
        @Test
        fun `対象成績が未報告のとき分娩結果が確定し報告済みの成績が楽観ロック付きで更新される`() {
            val breedingResult = BreedingFixture.breedingResult()
            val outcome = FoalingOutcome.LiveFoal(LocalDate.of(2025, 3, 20))
            val repository =
                mockk<BreedingResultRepository> {
                    every { findById(worldId, breedingResult.id) } returns breedingResult
                    every { save(worldId, any()) } answers { Ok(secondArg()) }
                }
            val useCase = ReportFoalingUseCase(repository)

            val result =
                useCase(actor, command(ReportFoalingCommand(breedingResult.id.value, outcome)))
                    .unwrap()

            assert(result.outcome == outcome)
            assert(result.id == breedingResult.id)
            // save には報告済み（outcome 確定後）の集約が渡ること
            verify(exactly = 1) { repository.save(worldId, match { it.outcome == outcome }) }
        }
    }

    @Nested
    inner class FailureCase {
        @Test
        fun `対象成績が見つからないとき BreedingResultNotFound を返し永続化されない`() {
            val breedingResultId = UUID.randomUUID()
            val repository =
                mockk<BreedingResultRepository> {
                    every { findById(worldId, BreedingResultId(breedingResultId)) } returns null
                }
            val useCase = ReportFoalingUseCase(repository)

            val result =
                useCase(
                    actor,
                    command(ReportFoalingCommand(breedingResultId, FoalingOutcome.NotConceived)),
                )

            assert(
                result.getError() ==
                    ReportFoalingUseCaseError.BreedingResultNotFound(breedingResultId)
            )
            verify(exactly = 0) { repository.save(worldId, any()) }
        }

        @Test
        fun `既に報告済みの成績へ再報告すると AlreadyReported を返し永続化されない`() {
            val first = FoalingOutcome.LiveFoal(LocalDate.of(2025, 3, 20))
            val reported = BreedingFixture.breedingResult().recordFoaling(first).unwrap()
            val repository =
                mockk<BreedingResultRepository> {
                    every { findById(worldId, reported.id) } returns reported
                }
            val useCase = ReportFoalingUseCase(repository)

            val result =
                useCase(
                    actor,
                    command(ReportFoalingCommand(reported.id.value, FoalingOutcome.NotConceived)),
                )

            assert(result.getError() == ReportFoalingUseCaseError.AlreadyReported(first))
            verify(exactly = 0) { repository.save(worldId, any()) }
        }

        @Test
        fun `更新が競合したとき ConcurrentModification を返す`() {
            val breedingResult = BreedingFixture.breedingResult()
            val repository =
                mockk<BreedingResultRepository> {
                    every { findById(worldId, breedingResult.id) } returns breedingResult
                    every { save(worldId, any()) } returns Err(UpdateConflict)
                }
            val useCase = ReportFoalingUseCase(repository)

            val result =
                useCase(
                    actor,
                    command(
                        ReportFoalingCommand(breedingResult.id.value, FoalingOutcome.NotConceived)
                    ),
                )

            assert(
                result.getError() ==
                    ReportFoalingUseCaseError.ConcurrentModification(breedingResult.id.value)
            )
        }
    }
}
