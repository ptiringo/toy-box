package com.example.api.replay

import com.example.api.application.studbook.breeding.StudCertificateInput
import com.example.api.application.studbook.horse.RegisterCarriedOverHorseCommand
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

/**
 * フィクスチャの分娩区分名。[FoalingOutcome] の各 variant と 1:1 に対応する。
 *
 * 種付せず（[FoalingOutcome.NotCovered]）は分娩結果としてフィクスチャに現れないため持たない（理由は [toOutcome] の KDoc）。variant
 * との対応の閉じ（増減の追従）は [toFixtureName] の網羅 when が compile 時に強制する。
 */
enum class FoalingOutcomeName {
    LiveFoal,
    NotConceived,
    Abortion,
    TwinAbortion,
    Stillbirth,
    TwinStillbirth,
    NeonatalDeath,
    TwinNeonatalDeath,
}

/**
 * フィクスチャの分娩区分名を [FoalingOutcome] に写す。未知の区分名は前提エラーとして例外にする（フィクスチャの記述ミス）。
 *
 * 種付せず（[FoalingOutcome.NotCovered]）はここに現れない。種付を行わなかった年は分娩結果の区分ではなく フィクスチャの
 * kind（UncoveredSeasonFixture）で表し、
 * [com.example.api.application.studbook.breeding.RecordUncoveredUseCase] が
 * 起こす終端の繁殖成績として実体化する。分娩結果として NotCovered を渡すと BreedingResult.recordFoaling が require で弾く（Err
 * ではなく例外）ため、写像の入口（[FoalingOutcomeName] に持たせない）で塞ぐ。
 */
fun FoalingFixture.toOutcome(): FoalingOutcome {
    val name = FoalingOutcomeName.entries.find { it.name == outcome } ?: error("未知の分娩区分名: $outcome")
    return when (name) {
        FoalingOutcomeName.LiveFoal ->
            FoalingOutcome.LiveFoal(
                LocalDate.parse(
                    requireNotNull(foalingDate) { "LiveFoal には foalingDate が必要: $this" }
                )
            )
        FoalingOutcomeName.NotConceived -> FoalingOutcome.NotConceived
        FoalingOutcomeName.Abortion -> FoalingOutcome.Abortion
        FoalingOutcomeName.TwinAbortion -> FoalingOutcome.TwinAbortion
        FoalingOutcomeName.Stillbirth -> FoalingOutcome.Stillbirth
        FoalingOutcomeName.TwinStillbirth -> FoalingOutcome.TwinStillbirth
        FoalingOutcomeName.NeonatalDeath -> FoalingOutcome.NeonatalDeath
        FoalingOutcomeName.TwinNeonatalDeath -> FoalingOutcome.TwinNeonatalDeath
    }
}

/**
 * [FoalingOutcome] の variant をフィクスチャの分娩区分名へ写す。
 *
 * sealed の網羅 when により、variant の増減を compile error として [FoalingOutcomeName]（ひいては [toOutcome]）へ
 * 伝えるトリップワイヤ。種付せず（[FoalingOutcome.NotCovered]）だけは分娩結果の区分名を持たないため null （フィクスチャでは kind＝uncovered
 * で表す）。
 */
fun FoalingOutcome.toFixtureName(): FoalingOutcomeName? =
    when (this) {
        is FoalingOutcome.LiveFoal -> FoalingOutcomeName.LiveFoal
        FoalingOutcome.NotConceived -> FoalingOutcomeName.NotConceived
        FoalingOutcome.Abortion -> FoalingOutcomeName.Abortion
        FoalingOutcome.TwinAbortion -> FoalingOutcomeName.TwinAbortion
        FoalingOutcome.Stillbirth -> FoalingOutcomeName.Stillbirth
        FoalingOutcome.TwinStillbirth -> FoalingOutcomeName.TwinStillbirth
        FoalingOutcome.NeonatalDeath -> FoalingOutcomeName.NeonatalDeath
        FoalingOutcome.TwinNeonatalDeath -> FoalingOutcomeName.TwinNeonatalDeath
        FoalingOutcome.NotCovered -> null
    }

/** フィクスチャの出典をラベル付きの一覧に写す（レポートに全件出し、典拠を辿れるようにする）。 */
fun FixtureSources.toSourceRefs(): List<SourceRef> =
    listOfNotNull(
        SourceRef("繁殖牝馬", broodmare),
        SourceRef("繁殖成績", breedingRecord),
        stallion?.let { SourceRef("種牡馬", it) },
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
 * 産地が日本＝内国産。seed の経路判定に使う（#633）。
 *
 * 内国産馬に輸入登録の事実は存在しないため移行取り込み（[carriedOverCommand]）で seed し、
 * 外国産の基礎馬は実際に輸入登録された馬なので輸入馬経路（[importedCommand]）で seed する。
 */
val HorseFacts.isDomestic: Boolean
    get() = originCountry == "日本"

/**
 * 公開事実（[facts]）と合成値（[synth]）を合成して輸入馬登録の入力を作る（外国産の基礎馬の seed）。
 *
 * 輸入登録は実際に起きた事実であり、合成しているのは JBIS 非公開の輸入年月日（[HorseSynthesized.landingDate]） だけで、事実である出生国は歪めない。
 */
fun importedCommand(facts: HorseFacts, synth: HorseSynthesized): RegisterImportedHorseCommand =
    RegisterImportedHorseCommand(
        sex = Sex.valueOf(facts.sex),
        coatColor = CoatColor.valueOf(facts.coatColor),
        breedType = BreedType.valueOf(facts.breedType),
        dateOfBirth = LocalDate.parse(facts.dateOfBirth),
        breeder = facts.breeder,
        microchipNumber = synth.microchipNumber,
        originCountry = facts.originCountry,
        landingDate =
            LocalDate.parse(
                requireNotNull(synth.landingDate) { "輸入馬経路の seed には landingDate が必要: $facts" }
            ),
        registrationNumber = synth.pedigreeRegistrationNumber,
    )

/**
 * 公開事実（[facts]）と合成値（[synth]）を合成して移行取り込み登録の入力を作る（内国産の基礎馬の seed。#633）。
 *
 * 輸入馬経路（[importedCommand]）と異なり揚陸日を要しないため、架空の輸入年月日の合成が不要になる。
 */
fun carriedOverCommand(
    facts: HorseFacts,
    synth: HorseSynthesized,
): RegisterCarriedOverHorseCommand =
    RegisterCarriedOverHorseCommand(
        sex = Sex.valueOf(facts.sex),
        coatColor = CoatColor.valueOf(facts.coatColor),
        breedType = BreedType.valueOf(facts.breedType),
        dateOfBirth = LocalDate.parse(facts.dateOfBirth),
        breeder = facts.breeder,
        microchipNumber = synth.microchipNumber,
        registrationNumber = synth.pedigreeRegistrationNumber,
    )

/** 暦日を Asia/Tokyo の正午の [Instant] に写す（提出日時の Command.issuedAt 用。締切 VO は Asia/Tokyo で暦日へ戻す）。 */
fun submissionInstant(date: LocalDate): Instant = date.atTime(12, 0).atZone(TOKYO).toInstant()

/** 与えた [Instant] に固定した [Clock]（Command.now に渡す）。 */
fun seasonClock(instant: Instant): Clock = Clock.fixed(instant, ZoneOffset.UTC)
