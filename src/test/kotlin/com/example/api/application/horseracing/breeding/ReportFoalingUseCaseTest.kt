package com.example.api.application.horseracing.breeding

import com.example.api.domain.horseracing.model.breeding.BreedingFixture
import com.example.api.domain.horseracing.model.breeding.BreedingResultId
import com.example.api.domain.horseracing.model.breeding.BreedingResultRepository
import com.example.api.domain.horseracing.model.breeding.FoalingOutcome
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

class ReportFoalingUseCaseTest {

    private fun command(payload: ReportFoalingCommand): Command<ReportFoalingCommand> =
        Command(payload, Instant.now())

    @Nested
    inner class SuccessCase {
        @Test
        fun `対象成績が未報告のとき分娩結果が確定し報告済みの成績が永続化される`() {
            val breedingResult = BreedingFixture.breedingResult()
            val outcome = FoalingOutcome.LiveFoal(LocalDate.of(2025, 3, 20))
            val repository =
                mockk<BreedingResultRepository> {
                    every { findById(breedingResult.id) } returns breedingResult
                    every { save(any()) } answers { firstArg() }
                }
            val useCase = ReportFoalingUseCase(repository)

            val result =
                useCase(command(ReportFoalingCommand(breedingResult.id.value, outcome))).unwrap()

            assert(result.outcome == outcome)
            assert(result.id == breedingResult.id)
            verify(exactly = 1) { repository.save(any()) }
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
            verify(exactly = 0) { repository.save(any()) }
        }

        @Test
        fun `既に報告済みの成績へ再報告すると AlreadyReported を返し永続化されない`() {
            val first = FoalingOutcome.LiveFoal(LocalDate.of(2025, 3, 20))
            val reported = BreedingFixture.breedingResult().recordFoaling(first).unwrap()
            val repository =
                mockk<BreedingResultRepository> { every { findById(reported.id) } returns reported }
            val useCase = ReportFoalingUseCase(repository)

            val result =
                useCase(
                    command(ReportFoalingCommand(reported.id.value, FoalingOutcome.NotConceived))
                )

            assert(result.getError() == ReportFoalingUseCaseError.AlreadyReported(first))
            verify(exactly = 0) { repository.save(any()) }
        }
    }
}
