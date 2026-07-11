package com.example.api.replay

import com.example.api.application.studbook.breeding.StudCertificateInput
import com.example.api.domain.studbook.model.breeding.FoalingOutcome
import com.example.api.replay.fixture.FoalingFixture
import com.example.api.replay.fixture.StudCertificateFixture
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

private val TOKYO: ZoneId = ZoneId.of("Asia/Tokyo")

/** フィクスチャの分娩区分名を [FoalingOutcome] に写す。未知の区分名は前提エラーとして例外にする（フィクスチャの記述ミス）。 */
fun FoalingFixture.toOutcome(): FoalingOutcome =
    when (outcome) {
        "LiveFoal" ->
            FoalingOutcome.LiveFoal(
                LocalDate.parse(
                    requireNotNull(foalingDate) { "LiveFoal には foalingDate が必要: $this" }
                )
            )
        "NotConceived" -> FoalingOutcome.NotConceived
        "Abortion" -> FoalingOutcome.Abortion
        "TwinAbortion" -> FoalingOutcome.TwinAbortion
        "Stillbirth" -> FoalingOutcome.Stillbirth
        "TwinStillbirth" -> FoalingOutcome.TwinStillbirth
        "NeonatalDeath" -> FoalingOutcome.NeonatalDeath
        "TwinNeonatalDeath" -> FoalingOutcome.TwinNeonatalDeath
        "NotCovered" -> FoalingOutcome.NotCovered
        else -> error("未知の分娩区分名: $outcome")
    }

/** 種付証明フィクスチャを [StudCertificateInput] に写す。 */
fun StudCertificateFixture.toInput(): StudCertificateInput =
    StudCertificateInput(
        number = number,
        validRegions = validRegions,
        validPeriodStart = LocalDate.parse(validPeriodStart),
        validPeriodEnd = LocalDate.parse(validPeriodEnd),
    )

/** 暦日を Asia/Tokyo の正午の [Instant] に写す（提出日時の Command.issuedAt 用。締切 VO は Asia/Tokyo で暦日へ戻す）。 */
fun submissionInstant(date: LocalDate): Instant = date.atTime(12, 0).atZone(TOKYO).toInstant()

/** 与えた [Instant] に固定した [Clock]（Command.now に渡す）。 */
fun seasonClock(instant: Instant): Clock = Clock.fixed(instant, ZoneOffset.UTC)
