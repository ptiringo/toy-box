package com.example.api.application.studbook.breeding

import com.example.api.domain.shared.Command
import com.example.api.domain.shared.UpdateConflict
import com.example.api.domain.shared.Versioned
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
                    every { findById(breedingResult.id) } returns Versioned(breedingResult, 0L)
                    every { update(any()) } answers { Ok(firstArg()) }
                }
            val useCase = ReportFoalingUseCase(repository)

            val result =
                useCase(command(ReportFoalingCommand(breedingResult.id.value, outcome))).unwrap()

            assert(result.outcome == outcome)
            assert(result.id == breedingResult.id)
            // 読み取り時点の version(0) を封筒のまま update へ運ぶ（楽観ロックの本義）
            verify(exactly = 1) {
                repository.update(match { it.version == 0L && it.value.outcome == outcome })
            }
            verify(exactly = 0) { repository.save(any()) }
        }
    }

    @Nested
    inner class FailureCase {
        @Test
        fun `対象成績が見つからないとき BreedingResultNotFound を返し永続化されない`() {
            val breedingResultId = UUID.randomUUID()
            val repository =
                mockk<BreedingResultRepository> {
                    every { findById(BreedingResultId(breedingResultId)) } returns null
                }
            val useCase = ReportFoalingUseCase(repository)

            val result =
                useCase(
                    command(ReportFoalingCommand(breedingResultId, FoalingOutcome.NotConceived))
                )

            assert(
                result.getError() ==
                    ReportFoalingUseCaseError.BreedingResultNotFound(breedingResultId)
            )
            verify(exactly = 0) { repository.update(any()) }
        }

        @Test
        fun `既に報告済みの成績へ再報告すると AlreadyReported を返し永続化されない`() {
            val first = FoalingOutcome.LiveFoal(LocalDate.of(2025, 3, 20))
            val reported = BreedingFixture.breedingResult().recordFoaling(first).unwrap()
            val repository =
                mockk<BreedingResultRepository> {
                    every { findById(reported.id) } returns Versioned(reported, 0L)
                }
            val useCase = ReportFoalingUseCase(repository)

            val result =
                useCase(
                    command(ReportFoalingCommand(reported.id.value, FoalingOutcome.NotConceived))
                )

            assert(result.getError() == ReportFoalingUseCaseError.AlreadyReported(first))
            verify(exactly = 0) { repository.update(any()) }
        }

        @Test
        fun `更新が競合したとき ConcurrentModification を返す`() {
            val breedingResult = BreedingFixture.breedingResult()
            val repository =
                mockk<BreedingResultRepository> {
                    every { findById(breedingResult.id) } returns Versioned(breedingResult, 0L)
                    every { update(any()) } returns Err(UpdateConflict)
                }
            val useCase = ReportFoalingUseCase(repository)

            val result =
                useCase(
                    command(
                        ReportFoalingCommand(breedingResult.id.value, FoalingOutcome.NotConceived)
                    )
                )

            assert(
                result.getError() ==
                    ReportFoalingUseCaseError.ConcurrentModification(breedingResult.id.value)
            )
        }
    }
}
