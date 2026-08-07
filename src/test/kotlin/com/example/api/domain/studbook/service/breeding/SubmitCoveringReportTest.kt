package com.example.api.domain.studbook.service.breeding

import com.example.api.domain.shared.WorldId
import com.example.api.domain.shared.generateId
import com.example.api.domain.studbook.model.breeding.BreedingFixture
import com.example.api.domain.studbook.model.breeding.CoveringReportRepository
import com.example.api.domain.studbook.model.breeding.SubmitCoveringReportError
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDate
import java.time.Year
import org.junit.jupiter.api.Test

/** 世界スコープ（#704）のテスト用フィクスチャ。ネストしたテストクラスからも参照できるようファイル直下に置く。 */
private val worldId = WorldId(generateId())

class SubmitCoveringReportTest {
    @Test
    fun `同年の提出が無ければ種付成績報告が生成されること`() {
        val stallionRegistration = BreedingFixture.stallionRegistration()
        val repository =
            mockk<CoveringReportRepository> {
                every {
                    findByStallionRegistrationIdAndCoveringYear(
                        worldId,
                        stallionRegistration.id,
                        Year.of(2024),
                    )
                } returns null
            }

        val report =
            submitCoveringReport(
                    worldId,
                    stallionRegistration = stallionRegistration,
                    coveringYear = Year.of(2024),
                    submittedOn = LocalDate.of(2024, 9, 1),
                    coveringReportRepository = repository,
                )
                .unwrap()

        assert(report.stallionRegistrationId == stallionRegistration.id)
        assert(report.coveringYear == Year.of(2024))
    }

    @Test
    fun `同一種牡馬×同一種付年に提出済みなら AlreadySubmittedForYear を返すこと`() {
        val stallionRegistration = BreedingFixture.stallionRegistration()
        val existing = BreedingFixture.coveringReport(stallionRegistration = stallionRegistration)
        val repository =
            mockk<CoveringReportRepository> {
                every {
                    findByStallionRegistrationIdAndCoveringYear(
                        worldId,
                        stallionRegistration.id,
                        Year.of(2024),
                    )
                } returns existing
            }

        val result =
            submitCoveringReport(
                worldId,
                stallionRegistration = stallionRegistration,
                coveringYear = Year.of(2024),
                submittedOn = LocalDate.of(2024, 9, 1),
                coveringReportRepository = repository,
            )

        assert(
            result.getError() ==
                SubmitCoveringReportError.AlreadySubmittedForYear(Year.of(2024), existing.id)
        )
    }

    @Test
    fun `繁殖牝馬の繁殖登録に対しては NotStallion を返すこと`() {
        val broodmareRegistration = BreedingFixture.breedingRegistration()
        val repository =
            mockk<CoveringReportRepository> {
                every { findByStallionRegistrationIdAndCoveringYear(worldId, any(), any()) } returns
                    null
            }

        val result =
            submitCoveringReport(
                worldId,
                stallionRegistration = broodmareRegistration,
                coveringYear = Year.of(2024),
                submittedOn = LocalDate.of(2024, 9, 1),
                coveringReportRepository = repository,
            )

        assert(result.getError() == SubmitCoveringReportError.NotStallion)
    }
}
