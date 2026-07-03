package com.example.api.domain.studbook.model.breeding

import java.time.Instant
import java.time.LocalDate
import java.time.Year
import org.junit.jupiter.api.Test

class BreedingReportDeadlineTest {

    @Test
    fun `繁殖年の翌年5月31日が提出期限になること`() {
        val deadline = BreedingReportDeadline.of(Year.of(2024))

        assert(deadline.date == LocalDate.of(2025, 5, 31))
    }

    @Test
    fun `期限日当日の提出は期限内であること`() {
        val deadline = BreedingReportDeadline.of(Year.of(2024))

        assert(!deadline.isMissedBy(LocalDate.of(2025, 5, 31)))
    }

    @Test
    fun `期限日翌日の提出は期限超過であること`() {
        val deadline = BreedingReportDeadline.of(Year.of(2024))

        assert(deadline.isMissedBy(LocalDate.of(2025, 6, 1)))
    }

    @Test
    fun `提出日時は日本の暦日（JST）に写されること`() {
        // UTC 14:59:59 = JST 23:59:59（同日 5/31）／ UTC 15:00:00 = JST 翌日 0:00:00（6/1）
        assert(
            BreedingReportDeadline.submissionDateOf(Instant.parse("2025-05-31T14:59:59Z")) ==
                LocalDate.of(2025, 5, 31)
        )
        assert(
            BreedingReportDeadline.submissionDateOf(Instant.parse("2025-05-31T15:00:00Z")) ==
                LocalDate.of(2025, 6, 1)
        )
    }
}
