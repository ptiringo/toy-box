package com.example.api.replay

import com.example.api.application.studbook.breeding.StudCertificateInput
import com.example.api.application.studbook.horse.RegisterImportedHorseCommand
import com.example.api.domain.studbook.model.breeding.FoalingOutcome
import com.example.api.domain.studbook.model.horse.bloodhorse.BreedType
import com.example.api.domain.studbook.model.horse.bloodhorse.CoatColor
import com.example.api.domain.studbook.model.horse.bloodhorse.Sex
import com.example.api.replay.fixture.FixtureSources
import com.example.api.replay.fixture.FoalingFixture
import com.example.api.replay.fixture.HorseFacts
import com.example.api.replay.fixture.HorseSynthesized
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

/** フィクスチャの出典をラベル付きの一覧に写す（レポートに全件出し、典拠を辿れるようにする）。 */
fun FixtureSources.toSourceRefs(): List<SourceRef> =
    listOfNotNull(
        SourceRef("繁殖牝馬", broodmare),
        SourceRef("繁殖成績", breedingRecord),
        SourceRef("種牡馬", stallion),
        foal?.let { SourceRef("産駒", it) },
    )

/** 種付証明フィクスチャを [StudCertificateInput] に写す。 */
fun StudCertificateFixture.toInput(): StudCertificateInput =
    StudCertificateInput(
        number = number,
        validRegions = validRegions,
        validPeriodStart = LocalDate.parse(validPeriodStart),
        validPeriodEnd = LocalDate.parse(validPeriodEnd),
    )

/**
 * 公開事実（[facts]）と合成値（[synth]）を合成して輸入馬登録の入力を作る。
 *
 * 内国産馬でも seed 経路が RegisterImportedHorse しかないため、出生国は facts になければ合成値を使う。
 */
fun importedCommand(facts: HorseFacts, synth: HorseSynthesized): RegisterImportedHorseCommand =
    RegisterImportedHorseCommand(
        sex = Sex.valueOf(facts.sex),
        coatColor = CoatColor.valueOf(facts.coatColor),
        breedType = BreedType.valueOf(facts.breedType),
        dateOfBirth = LocalDate.parse(facts.dateOfBirth),
        breeder = facts.breeder,
        microchipNumber = synth.microchipNumber,
        originCountry = facts.originCountry ?: synth.originCountry,
        landingDate = LocalDate.parse(synth.landingDate),
        registrationNumber = synth.pedigreeRegistrationNumber,
    )

/** 暦日を Asia/Tokyo の正午の [Instant] に写す（提出日時の Command.issuedAt 用。締切 VO は Asia/Tokyo で暦日へ戻す）。 */
fun submissionInstant(date: LocalDate): Instant = date.atTime(12, 0).atZone(TOKYO).toInstant()

/** 与えた [Instant] に固定した [Clock]（Command.now に渡す）。 */
fun seasonClock(instant: Instant): Clock = Clock.fixed(instant, ZoneOffset.UTC)
