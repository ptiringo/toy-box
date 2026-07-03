package com.example.api.domain.studbook.model.breeding

import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import java.time.LocalDate
import org.junit.jupiter.api.Test

class BreedingResultSubmitReportTest {

    /** 分娩結果確定済み（提出可能）な繁殖成績。繁殖年 2024 → 提出期限は 2025-05-31。 */
    private val reported =
        BreedingFixture.breedingResult()
            .recordFoaling(FoalingOutcome.LiveFoal(LocalDate.of(2025, 3, 20)))
            .unwrap()

    @Test
    fun `分娩結果確定済みの成績は期限日当日に提出でき期限内と導出されること`() {
        val submitted = reported.submitReport(LocalDate.of(2025, 5, 31)).unwrap()

        assert(submitted.id == reported.id)
        assert(submitted.reportSubmittedOn == LocalDate.of(2025, 5, 31))
        assert(submitted.reportSubmittedLate == false)
    }

    @Test
    fun `期限超過の提出も受理され期限超過と導出されること`() {
        val submitted = reported.submitReport(LocalDate.of(2025, 6, 1)).unwrap()

        assert(submitted.reportSubmittedOn == LocalDate.of(2025, 6, 1))
        assert(submitted.reportSubmittedLate == true)
    }

    @Test
    fun `種付せずの年次成績も提出できること`() {
        val uncovered = BreedingFixture.uncoveredBreedingResult()

        val submitted = uncovered.submitReport(LocalDate.of(2025, 4, 1)).unwrap()

        assert(submitted.reportSubmittedOn == LocalDate.of(2025, 4, 1))
        assert(submitted.reportSubmittedLate == false)
    }

    @Test
    fun `分娩結果未確定の成績は提出できないこと`() {
        val unreported = BreedingFixture.breedingResult()

        val error = unreported.submitReport(LocalDate.of(2025, 5, 1)).getError()

        assert(error == SubmitBreedingReportError.OutcomeNotRecorded)
    }

    @Test
    fun `提出済みの成績への再提出は既存の提出日付きで拒まれ元は変わらないこと`() {
        val submitted = reported.submitReport(LocalDate.of(2025, 5, 1)).unwrap()

        val error = submitted.submitReport(LocalDate.of(2025, 5, 2)).getError()

        assert(error == SubmitBreedingReportError.ReportAlreadySubmitted(LocalDate.of(2025, 5, 1)))
        assert(submitted.reportSubmittedOn == LocalDate.of(2025, 5, 1))
    }

    @Test
    fun `未提出の成績の期限超過導出はnullであること`() {
        assert(reported.reportSubmittedLate == null)
    }
}
