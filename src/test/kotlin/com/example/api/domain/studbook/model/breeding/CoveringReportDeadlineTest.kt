package com.example.api.domain.studbook.model.breeding

import java.time.Instant
import java.time.LocalDate
import java.time.Year
import org.junit.jupiter.api.Test

class CoveringReportDeadlineTest {

    @Test
    fun `種付年の当年9月30日が提出期限になること`() {
        val deadline = CoveringReportDeadline.of(Year.of(2024))

        assert(deadline.date == LocalDate.of(2024, 9, 30))
    }

    @Test
    fun `期限日当日の提出は期限内であること`() {
        val deadline = CoveringReportDeadline.of(Year.of(2024))

        assert(!deadline.isMissedBy(LocalDate.of(2024, 9, 30)))
    }

    @Test
    fun `期限日翌日の提出は期限超過であること`() {
        val deadline = CoveringReportDeadline.of(Year.of(2024))

        assert(deadline.isMissedBy(LocalDate.of(2024, 10, 1)))
    }

    @Test
    fun `提出日時は日本の暦日（JST）に写されること`() {
        // UTC 14:59:59 = JST 23:59:59（同日 9/30）／ UTC 15:00:00 = JST 翌日 0:00:00（10/1）
        assert(
            CoveringReportDeadline.submissionDateOf(Instant.parse("2024-09-30T14:59:59Z")) ==
                LocalDate.of(2024, 9, 30)
        )
        assert(
            CoveringReportDeadline.submissionDateOf(Instant.parse("2024-09-30T15:00:00Z")) ==
                LocalDate.of(2024, 10, 1)
        )
    }
}
