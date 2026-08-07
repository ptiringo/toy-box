package com.example.api.application.studbook.breeding

import com.example.api.domain.shared.AccountId
import com.example.api.domain.shared.Actor
import com.example.api.domain.shared.Command
import com.example.api.domain.shared.WorldId
import com.example.api.domain.shared.generateId
import com.example.api.domain.studbook.model.breeding.BreedingFixture
import com.example.api.domain.studbook.model.breeding.BreedingRegistrationId
import com.example.api.domain.studbook.model.breeding.BreedingRegistrationRepository
import com.example.api.domain.studbook.model.breeding.CoveringReportRepository
import com.example.api.domain.studbook.model.breeding.SubmitCoveringReportError
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import java.time.LocalDate
import java.time.Year
import java.util.UUID
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/** 世界スコープ（#704）のテスト用フィクスチャ。ネストしたテストクラスからも参照できるようファイル直下に置く。 */
private val worldId = WorldId(generateId())
private val actor = Actor(accountId = AccountId(generateId()), worldId = worldId)

class SubmitCoveringReportUseCaseTest {
    private fun command(
        stallionBreedingRegistrationId: UUID,
        coveringYear: Int,
        issuedAt: Instant,
    ): Command<SubmitCoveringReportCommand> =
        Command(SubmitCoveringReportCommand(stallionBreedingRegistrationId, coveringYear), issuedAt)

    @Nested
    inner class SuccessCase {
        @Test
        fun `issuedAtがJSTの提出日として消費され種付成績報告が保存される`() {
            val stallionRegistration = BreedingFixture.stallionRegistration()
            val registrationRepository =
                mockk<BreedingRegistrationRepository> {
                    every { findById(worldId, stallionRegistration.id) } returns
                        stallionRegistration
                }
            val reportRepository =
                mockk<CoveringReportRepository> {
                    every {
                        findByStallionRegistrationIdAndCoveringYear(
                            worldId,
                            stallionRegistration.id,
                            Year.of(2024),
                        )
                    } returns null
                    every { save(worldId, any()) } answers { Ok(secondArg()) }
                }
            val useCase = SubmitCoveringReportUseCase(registrationRepository, reportRepository)
            // UTC 15:00 = JST 翌日 0:00 → 提出日は 10/1（期限 9/30 を超過）
            val issuedAt = Instant.parse("2024-09-30T15:00:00Z")

            val result =
                useCase(actor, command(stallionRegistration.id.value, 2024, issuedAt)).unwrap()

            assert(result.submittedOn == LocalDate.of(2024, 10, 1))
            assert(result.submittedLate)
            verify(exactly = 1) { reportRepository.save(worldId, any()) }
        }

        @Test
        fun `当年の種付記録が1件も無くても提出は受理される`() {
            // 種付実績の有無は前提条件にしない（#540 の確定事項）。BreedingResult への照会は行わない。
            val stallionRegistration = BreedingFixture.stallionRegistration()
            val registrationRepository =
                mockk<BreedingRegistrationRepository> {
                    every { findById(worldId, stallionRegistration.id) } returns
                        stallionRegistration
                }
            val reportRepository =
                mockk<CoveringReportRepository> {
                    every {
                        findByStallionRegistrationIdAndCoveringYear(worldId, any(), any())
                    } returns null
                    every { save(worldId, any()) } answers { Ok(secondArg()) }
                }
            val useCase = SubmitCoveringReportUseCase(registrationRepository, reportRepository)

            val result =
                useCase(
                        actor,
                        command(
                            stallionRegistration.id.value,
                            2024,
                            Instant.parse("2024-09-01T00:00:00Z"),
                        ),
                    )
                    .unwrap()

            assert(result.coveringYear == Year.of(2024))
        }
    }

    @Nested
    inner class FailureCase {
        @Test
        fun `繁殖登録が見つからないとき StallionRegistrationNotFound を返し永続化されない`() {
            val unknownId = UUID.randomUUID()
            val registrationRepository =
                mockk<BreedingRegistrationRepository> {
                    every { findById(worldId, BreedingRegistrationId(unknownId)) } returns null
                }
            val reportRepository = mockk<CoveringReportRepository>()
            val useCase = SubmitCoveringReportUseCase(registrationRepository, reportRepository)

            val result =
                useCase(actor, command(unknownId, 2024, Instant.parse("2024-09-01T00:00:00Z")))

            assert(
                result.getError() ==
                    SubmitCoveringReportUseCaseError.StallionRegistrationNotFound(unknownId)
            )
            verify(exactly = 0) { reportRepository.save(worldId, any()) }
        }

        @Test
        fun `繁殖牝馬の登録を指定したとき PreconditionViolated(NotStallion) を返し永続化されない`() {
            val broodmareRegistration = BreedingFixture.breedingRegistration()
            val registrationRepository =
                mockk<BreedingRegistrationRepository> {
                    every { findById(worldId, broodmareRegistration.id) } returns
                        broodmareRegistration
                }
            val reportRepository =
                mockk<CoveringReportRepository> {
                    every {
                        findByStallionRegistrationIdAndCoveringYear(worldId, any(), any())
                    } returns null
                }
            val useCase = SubmitCoveringReportUseCase(registrationRepository, reportRepository)

            val result =
                useCase(
                    actor,
                    command(
                        broodmareRegistration.id.value,
                        2024,
                        Instant.parse("2024-09-01T00:00:00Z"),
                    ),
                )

            assert(
                result.getError() ==
                    SubmitCoveringReportUseCaseError.PreconditionViolated(
                        SubmitCoveringReportError.NotStallion
                    )
            )
            verify(exactly = 0) { reportRepository.save(worldId, any()) }
        }

        @Test
        fun `同年に提出済みのとき PreconditionViolated(AlreadySubmittedForYear) を返し永続化されない`() {
            val stallionRegistration = BreedingFixture.stallionRegistration()
            val existing =
                BreedingFixture.coveringReport(stallionRegistration = stallionRegistration)
            val registrationRepository =
                mockk<BreedingRegistrationRepository> {
                    every { findById(worldId, stallionRegistration.id) } returns
                        stallionRegistration
                }
            val reportRepository =
                mockk<CoveringReportRepository> {
                    every {
                        findByStallionRegistrationIdAndCoveringYear(
                            worldId,
                            stallionRegistration.id,
                            Year.of(2024),
                        )
                    } returns existing
                }
            val useCase = SubmitCoveringReportUseCase(registrationRepository, reportRepository)

            val result =
                useCase(
                    actor,
                    command(
                        stallionRegistration.id.value,
                        2024,
                        Instant.parse("2024-09-01T00:00:00Z"),
                    ),
                )

            assert(
                result.getError() ==
                    SubmitCoveringReportUseCaseError.PreconditionViolated(
                        SubmitCoveringReportError.AlreadySubmittedForYear(
                            Year.of(2024),
                            existing.id,
                        )
                    )
            )
            verify(exactly = 0) { reportRepository.save(worldId, any()) }
        }
    }
}
