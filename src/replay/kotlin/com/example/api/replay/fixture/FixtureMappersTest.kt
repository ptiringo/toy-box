package com.example.api.replay.fixture

import com.example.api.domain.studbook.model.breeding.FoalingOutcome
import com.example.api.replay.submissionInstant
import com.example.api.replay.toInput
import com.example.api.replay.toOutcome
import java.time.LocalDate
import java.time.ZoneId
import org.junit.jupiter.api.Test

class FixtureMappersTest {
    @Test
    fun `LiveFoal は分娩日つきの区分に写る`() {
        val outcome = FoalingFixture("LiveFoal", "2002-03-25").toOutcome()

        assert(outcome == FoalingOutcome.LiveFoal(LocalDate.of(2002, 3, 25)))
    }

    @Test
    fun `産駒なしの合成区分は NotConceived に写る`() {
        assert(FoalingFixture("NotConceived").toOutcome() == FoalingOutcome.NotConceived)
    }

    @Test
    fun `種付証明フィクスチャが入力へ写る`() {
        val input =
            StudCertificateFixture("SC-2001-0001", listOf("北海道"), "2001-02-01", "2001-07-31")
                .toInput()

        assert(input.number == "SC-2001-0001")
        assert(input.validRegions == listOf("北海道"))
        assert(input.validPeriodStart == LocalDate.of(2001, 2, 1))
    }

    @Test
    fun `提出日は Asia Tokyo 正午の Instant に写り 9-30 が期限内側に収まる`() {
        // 2001-09-30 正午(JST) は当年 9/30 期限（当日は期限内）に収まる。
        val instant = submissionInstant(LocalDate.of(2001, 9, 30))
        val expected =
            LocalDate.of(2001, 9, 30).atTime(12, 0).atZone(ZoneId.of("Asia/Tokyo")).toInstant()

        assert(instant == expected)
    }
}
