package com.example.api.replay.fixture

import com.example.api.domain.studbook.model.breeding.FoalingOutcome
import com.example.api.replay.submissionInstant
import com.example.api.replay.toInput
import com.example.api.replay.toOutcome
import java.time.LocalDate
import org.junit.jupiter.api.Test

class FixtureMappersTest {
    @Test
    fun `LiveFoal は foalingDate を保持して写る`() {
        val outcome = FoalingFixture("LiveFoal", "2020-03-20").toOutcome()
        assert(outcome == FoalingOutcome.LiveFoal(LocalDate.of(2020, 3, 20)))
    }

    @Test
    fun `不受胎は NotConceived に写る`() {
        assert(FoalingFixture("NotConceived", null).toOutcome() == FoalingOutcome.NotConceived)
    }

    @Test
    fun `種付証明フィクスチャが入力へ写る`() {
        val input =
            StudCertificateFixture("SC-1", listOf("北海道"), "2019-02-01", "2019-07-31").toInput()
        assert(input.number == "SC-1")
        assert(input.validRegions == listOf("北海道"))
        assert(input.validPeriodStart == LocalDate.of(2019, 2, 1))
    }

    @Test
    fun `提出日は Asia Tokyo 正午の Instant に写り 9-30 が期限内側に収まる`() {
        // 2019-09-30 正午(JST) は当年 9/30 期限（当日は期限内）に収まる
        val instant = submissionInstant(LocalDate.of(2019, 9, 30))
        val expected =
            LocalDate.of(2019, 9, 30)
                .atTime(12, 0)
                .atZone(java.time.ZoneId.of("Asia/Tokyo"))
                .toInstant()
        assert(instant == expected)
    }
}
