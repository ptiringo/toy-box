package com.example.api.domain.studbook.model.breeding

import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import java.time.LocalDate
import org.junit.jupiter.api.Test

/** [ValidityPeriod] のユニットテスト */
class ValidityPeriodTest {
    @Test
    fun `終点が起点以降なら生成できること`() {
        val period =
            ValidityPeriod.create(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31)).unwrap()

        assert(period.start == LocalDate.of(2024, 1, 1))
        assert(period.end == LocalDate.of(2024, 12, 31))
    }

    @Test
    fun `起点と終点が同日でも生成できること`() {
        val day = LocalDate.of(2024, 4, 1)

        val period = ValidityPeriod.create(day, day).unwrap()

        assert(period.start == day && period.end == day)
    }

    @Test
    fun `終点が起点より前だと InvalidValidityPeriod を返すこと`() {
        val start = LocalDate.of(2024, 12, 31)
        val end = LocalDate.of(2024, 1, 1)

        val result = ValidityPeriod.create(start, end)

        assert(result.getError() == InvalidValidityPeriod(start, end))
    }

    @Test
    fun `contains は起点・終点の当日を含むこと`() {
        val period =
            ValidityPeriod.create(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31)).unwrap()

        assert(period.contains(LocalDate.of(2024, 1, 1)))
        assert(period.contains(LocalDate.of(2024, 12, 31)))
        assert(period.contains(LocalDate.of(2024, 6, 1)))
    }

    @Test
    fun `contains は期間外の日を含まないこと`() {
        val period =
            ValidityPeriod.create(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31)).unwrap()

        assert(!period.contains(LocalDate.of(2023, 12, 31)))
        assert(!period.contains(LocalDate.of(2025, 1, 1)))
    }
}
