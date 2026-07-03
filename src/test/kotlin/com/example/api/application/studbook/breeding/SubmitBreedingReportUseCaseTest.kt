package com.example.api.application.studbook.breeding

import com.example.api.domain.shared.Command
import com.example.api.domain.shared.UpdateConflict
import com.example.api.domain.studbook.model.breeding.BreedingFixture
import com.example.api.domain.studbook.model.breeding.BreedingResult
import com.example.api.domain.studbook.model.breeding.BreedingResultId
import com.example.api.domain.studbook.model.breeding.BreedingResultRepository
import com.example.api.domain.studbook.model.breeding.FoalingOutcome
import com.example.api.domain.studbook.model.breeding.SubmitBreedingReportError
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

class SubmitBreedingReportUseCaseTest {

    /** 分娩結果確定済み（提出可能）な繁殖成績。繁殖年 2024 → 提出期限は 2025-05-31。 */
    private fun reportedResult(): BreedingResult =
        BreedingFixture.breedingResult()
            .recordFoaling(FoalingOutcome.LiveFoal(LocalDate.of(2025, 3, 20)))
            .unwrap()

    private fun command(
        breedingResultId: UUID,
        issuedAt: Instant,
    ): Command<SubmitBreedingReportCommand> =
        Command(SubmitBreedingReportCommand(breedingResultId), issuedAt)

    @Nested
    inner class SuccessCase {
        @Test
        fun `issuedAtがJSTの提出日として消費され提出済みの成績が楽観ロック付きで更新される`() {
            val reported = reportedResult()
            val repository =
                mockk<BreedingResultRepository> {
                    every { findById(reported.id) } returns reported
                    every { save(any()) } answers { Ok(firstArg()) }
                }
            val useCase = SubmitBreedingReportUseCase(repository)
            // UTC 15:00 = JST 翌日 0:00 → 提出日は 6/1（期限 5/31 を超過）
            val issuedAt = Instant.parse("2025-05-31T15:00:00Z")

            val result = useCase(command(reported.id.value, issuedAt)).unwrap()

            assert(result.reportSubmittedOn == LocalDate.of(2025, 6, 1))
            assert(result.reportSubmittedLate == true)
            // save には提出済みの集約が渡ること
            verify(exactly = 1) { repository.save(match { it.reportSubmittedOn != null }) }
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
            val useCase = SubmitBreedingReportUseCase(repository)

            val result = useCase(command(breedingResultId, Instant.parse("2025-05-01T00:00:00Z")))

            assert(
                result.getError() ==
                    SubmitBreedingReportUseCaseError.BreedingResultNotFound(breedingResultId)
            )
            verify(exactly = 0) { repository.save(any()) }
        }

        @Test
        fun `分娩結果未確定のとき PreconditionViolated を返し永続化されない`() {
            val unreported = BreedingFixture.breedingResult()
            val repository =
                mockk<BreedingResultRepository> {
                    every { findById(unreported.id) } returns unreported
                }
            val useCase = SubmitBreedingReportUseCase(repository)

            val result =
                useCase(command(unreported.id.value, Instant.parse("2025-05-01T00:00:00Z")))

            assert(
                result.getError() ==
                    SubmitBreedingReportUseCaseError.PreconditionViolated(
                        SubmitBreedingReportError.OutcomeNotRecorded
                    )
            )
            verify(exactly = 0) { repository.save(any()) }
        }

        @Test
        fun `提出済みのとき PreconditionViolated を返し永続化されない`() {
            val submitted = reportedResult().submitReport(LocalDate.of(2025, 5, 1)).unwrap()
            val repository =
                mockk<BreedingResultRepository> {
                    every { findById(submitted.id) } returns submitted
                }
            val useCase = SubmitBreedingReportUseCase(repository)

            val result = useCase(command(submitted.id.value, Instant.parse("2025-05-02T00:00:00Z")))

            assert(
                result.getError() ==
                    SubmitBreedingReportUseCaseError.PreconditionViolated(
                        SubmitBreedingReportError.ReportAlreadySubmitted(LocalDate.of(2025, 5, 1))
                    )
            )
            verify(exactly = 0) { repository.save(any()) }
        }

        @Test
        fun `更新が競合したとき ConcurrentModification を返す`() {
            val reported = reportedResult()
            val repository =
                mockk<BreedingResultRepository> {
                    every { findById(reported.id) } returns reported
                    every { save(any()) } returns Err(UpdateConflict)
                }
            val useCase = SubmitBreedingReportUseCase(repository)

            val result = useCase(command(reported.id.value, Instant.parse("2025-05-01T00:00:00Z")))

            assert(
                result.getError() ==
                    SubmitBreedingReportUseCaseError.ConcurrentModification(reported.id.value)
            )
        }
    }
}
