package com.example.api.replay

import com.example.api.application.studbook.breeding.RecordCoveringCommand
import com.example.api.application.studbook.breeding.RecordCoveringUseCase
import com.example.api.application.studbook.breeding.RecordOverseasCoveringUseCase
import com.example.api.application.studbook.breeding.RecordUncoveredCommand
import com.example.api.application.studbook.breeding.RecordUncoveredUseCase
import com.example.api.application.studbook.breeding.RegisterBreedingRegistrationCommand
import com.example.api.application.studbook.breeding.RegisterBreedingRegistrationUseCase
import com.example.api.application.studbook.breeding.ReportFoalingCommand
import com.example.api.application.studbook.breeding.ReportFoalingUseCase
import com.example.api.application.studbook.breeding.SubmitBreedingReportCommand
import com.example.api.application.studbook.breeding.SubmitBreedingReportUseCase
import com.example.api.application.studbook.breeding.SubmitCoveringReportCommand
import com.example.api.application.studbook.breeding.SubmitCoveringReportUseCase
import com.example.api.application.studbook.horse.NameHorseCommand
import com.example.api.application.studbook.horse.NameHorseUseCase
import com.example.api.application.studbook.horse.RegisterBroughtInFoalCommand
import com.example.api.application.studbook.horse.RegisterBroughtInFoalUseCase
import com.example.api.application.studbook.horse.RegisterCarriedOverHorseUseCase
import com.example.api.application.studbook.horse.RegisterFoalCommand
import com.example.api.application.studbook.horse.RegisterFoalUseCase
import com.example.api.application.studbook.horse.RegisterImportedHorseUseCase
import com.example.api.application.studbook.horse.RegisteredBloodHorse
import com.example.api.domain.shared.Actor
import com.example.api.domain.shared.Command
import com.example.api.domain.studbook.model.breeding.BreedingRegistration
import com.example.api.domain.studbook.model.breeding.BreedingResult
import com.example.api.domain.studbook.model.horse.bloodhorse.BreedType
import com.example.api.domain.studbook.model.horse.bloodhorse.CoatColor
import com.example.api.domain.studbook.model.horse.bloodhorse.Sex
import com.example.api.domain.studbook.model.inspection.DnaParentageResult
import com.example.api.replay.fixture.CoveredSeasonFixture
import com.example.api.replay.fixture.CoveredSynthesized
import com.example.api.replay.fixture.DomesticCoveringFixture
import com.example.api.replay.fixture.FoalFacts
import com.example.api.replay.fixture.FoalSynthesized
import com.example.api.replay.fixture.HorseFacts
import com.example.api.replay.fixture.HorseFixture
import com.example.api.replay.fixture.HorseSynthesized
import com.example.api.replay.fixture.OverseasCoveringFixture
import com.example.api.replay.fixture.UncoveredSeasonFixture
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.fold
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.Year
import org.springframework.stereotype.Component

/**
 * 実在馬フィクスチャを繁殖ワークフローへ一周流し込むエンジン。 各ユースケースを順に駆動し、成功なら次段へ ID を渡し、失敗（Err）ならその段階で打ち切って観測を返す。 Err
 * はハーネスの失敗ではなく「発見」として記録する（例外にしない）。
 */
@Component
class ReplayEngine(
    private val registerImportedHorse: RegisterImportedHorseUseCase,
    private val registerCarriedOverHorse: RegisterCarriedOverHorseUseCase,
    private val registerBreedingRegistration: RegisterBreedingRegistrationUseCase,
    private val recordCovering: RecordCoveringUseCase,
    private val recordOverseasCovering: RecordOverseasCoveringUseCase,
    private val recordUncovered: RecordUncoveredUseCase,
    private val submitCoveringReport: SubmitCoveringReportUseCase,
    private val reportFoaling: ReportFoalingUseCase,
    private val registerFoal: RegisterFoalUseCase,
    private val registerBroughtInFoal: RegisterBroughtInFoalUseCase,
    private val nameHorse: NameHorseUseCase,
    private val submitBreedingReport: SubmitBreedingReportUseCase,
) {
    /**
     * 1 頭分のフィクスチャを繁殖ワークフローへ通す。
     *
     * [actor] はこのハーネスが書き込む世界（セーブデータ）を指す（#704 / ADR-0067）。ハーネス自身は認可を 扱わず、呼び出し側（テスト）が用意した世界にすべての行を書く。
     */
    fun run(actor: Actor, fixture: HorseFixture): HorseReplayOutcome =
        when (fixture) {
            is CoveredSeasonFixture -> runCovered(actor, fixture)
            is UncoveredSeasonFixture -> runUncovered(actor, fixture)
        }

    /**
     * 基礎馬（種牡馬・繁殖牝馬）1 頭を seed する。
     *
     * 産地が日本（内国産）なら移行取り込み経路、外国産なら輸入馬経路で登録する（#633。導出の根拠は [com.example.api.replay.fixture.HorseFacts]
     * と isDomestic の KDoc）。
     */
    private fun seedHorse(
        actor: Actor,
        facts: HorseFacts,
        synth: HorseSynthesized,
        clock: Clock,
    ): Result<RegisteredBloodHorse, Any> =
        if (facts.isDomestic) {
            registerCarriedOverHorse(actor, Command.now(carriedOverCommand(facts, synth), clock))
        } else {
            registerImportedHorse(actor, Command.now(importedCommand(facts, synth), clock))
        }

    /**
     * 種付を行った年の経路: seed ×2 → 繁殖登録 ×2 → 種付 → 種付成績報告 → 出生報告 → 産駒登録 → 馬名登録 → 繁殖成績報告。
     *
     * 海外種付（持込馬の経路。#624）の年は、父が日本の繁殖登録を持たない外国種牡馬なので、種牡馬の seed・繁殖登録・
     * 種付成績報告（雄側）を通らない。種付は海外種付として、産駒は持込馬として登録する。
     */
    private fun runCovered(actor: Actor, fixture: CoveredSeasonFixture): HorseReplayOutcome {
        val session = ReplaySession(fixture)
        val facts = fixture.facts
        val synth = fixture.synthesized
        // 提出以外のドメイン日付は論理に効かないので、シーズン中の固定 Instant を使う。
        // 種付年は公開事実ではなく表示年からの換算（合成値）なので synthesized 側から取る。
        val neutralInstant: Instant = submissionInstant(LocalDate.of(synth.coveringYear, 4, 1))
        val neutralClock = seasonClock(neutralInstant)
        // 国内種付のときだけ種牡馬を seed する（海外種付の父は外国種牡馬で、日本の登録を持たない）。
        val stallionSynth =
            if (synth.covering is DomesticCoveringFixture) {
                requireNotNull(synth.stallion) { "国内種付には種牡馬の合成値が必要: ${fixture.name}" }
            } else {
                null
            }

        // 0. 基礎馬（種牡馬・繁殖牝馬）を seed（親不在で登録可）。
        //    産地が日本なら移行取り込み、外国産なら輸入馬経路（#633）。
        val stallion = stallionSynth?.let { stallionSynthesized ->
            val seeded =
                session.step(
                    ReplayStep.REGISTER_STALLION,
                    seedHorse(actor, facts.stallion, stallionSynthesized, neutralClock),
                ) ?: return session.stop()
            seeded to stallionSynthesized
        }
        val broodmare =
            session.step(
                ReplayStep.REGISTER_BROODMARE,
                seedHorse(actor, facts.broodmare, synth.broodmare, neutralClock),
            ) ?: return session.stop()

        // 1. 繁殖登録（雄・雌）。
        val stallionBreeding = stallion?.let { (seeded, stallionSynthesized) ->
            session.step(
                ReplayStep.REGISTER_STALLION_BREEDING,
                registerBreeding(actor, seeded, stallionSynthesized, neutralClock),
            ) ?: return session.stop()
        }
        val broodmareBreeding =
            session.step(
                ReplayStep.REGISTER_BROODMARE_BREEDING,
                registerBreeding(actor, broodmare, synth.broodmare, neutralClock),
            ) ?: return session.stop()

        // 2. 種付記録（国内種付／海外種付）。
        val breedingResult =
            session.step(
                ReplayStep.RECORD_COVERING,
                recordCoveringOf(actor, fixture, broodmareBreeding, stallionBreeding, neutralClock),
            ) ?: return session.stop()

        // 3. 種付成績報告（雄側・当年 9/30 期限）。提出日を Command.issuedAt に反映。
        //    海外種付の年は外国種牡馬に JAIRS の種付成績報告が無いので通らない。
        if (stallionBreeding != null) {
            val submittedOn =
                requireNotNull(synth.submissions.coveringReportSubmittedOn) {
                    "国内種付には種付成績報告の提出日が必要: ${fixture.name}"
                }
            session.step(
                ReplayStep.SUBMIT_COVERING_REPORT,
                submitCoveringReport(
                    actor,
                    Command.now(
                        SubmitCoveringReportCommand(stallionBreeding.id.value, synth.coveringYear),
                        seasonClock(submissionInstant(LocalDate.parse(submittedOn))),
                    ),
                ),
            ) ?: return session.stop()
        }

        // 4. 出生報告。
        session.step(
            ReplayStep.REPORT_FOALING,
            reportFoaling(
                actor,
                Command.now(
                    ReportFoalingCommand(breedingResult.id.value, facts.foaling.toOutcome()),
                    neutralClock,
                ),
            ),
        ) ?: return session.stop()

        // 5. 産駒血統登録 → 6. 馬名登録（LiveFoal かつ産駒情報があるときのみ）。
        //    未命名の産駒（JBIS 上も馬名が付いていない）は馬名登録を行わない。
        val foalFacts = facts.foal
        val foalSynth = synth.foal
        if (foalFacts != null && foalSynth != null) {
            val registeredFoal =
                session.step(
                    ReplayStep.REGISTER_FOAL,
                    registerFoalOf(
                        actor,
                        synth,
                        breedingResult,
                        foalFacts,
                        foalSynth,
                        neutralClock,
                    ),
                ) ?: return session.stop()

            val foalName = foalFacts.name
            if (foalName != null) {
                session.step(
                    ReplayStep.NAME_FOAL,
                    nameHorse(
                        actor,
                        Command.now(
                            NameHorseCommand(registeredFoal.bloodHorse.id.value, foalName),
                            neutralClock,
                        ),
                    ),
                ) ?: return session.stop()
            }
        }

        // 7. 繁殖成績報告（雌側・翌年 5/31 期限）。
        session.step(
            ReplayStep.SUBMIT_BREEDING_REPORT,
            submitBreedingReport(
                actor,
                Command.now(
                    SubmitBreedingReportCommand(breedingResult.id.value),
                    seasonClock(
                        submissionInstant(
                            LocalDate.parse(synth.submissions.breedingReportSubmittedOn)
                        )
                    ),
                ),
            ),
        ) ?: return session.stop()

        return session.complete()
    }

    /** seed 済みの基礎馬を繁殖登録する。 */
    private fun registerBreeding(
        actor: Actor,
        horse: RegisteredBloodHorse,
        synth: HorseSynthesized,
        clock: Clock,
    ): Result<BreedingRegistration, Any> =
        registerBreedingRegistration(
            actor,
            Command.now(
                RegisterBreedingRegistrationCommand(
                    horse.bloodHorse.id.value,
                    synth.breedingRegistrationNumber,
                ),
                clock,
            ),
        )

    /** 種付を記録する。国内種付は種牡馬の繁殖登録で、海外種付は外国種牡馬の公開事実で父を指す（#624）。 */
    private fun recordCoveringOf(
        actor: Actor,
        fixture: CoveredSeasonFixture,
        broodmareBreeding: BreedingRegistration,
        stallionBreeding: BreedingRegistration?,
        clock: Clock,
    ): Result<BreedingResult, Any> =
        when (val covering = fixture.synthesized.covering) {
            is DomesticCoveringFixture ->
                recordCovering(
                    actor,
                    Command.now(
                        RecordCoveringCommand(
                            breedingRegistrationId = broodmareBreeding.id.value,
                            stallionRegistrationId = checkNotNull(stallionBreeding).id.value,
                            coveringDate = LocalDate.parse(covering.coveringDate),
                            coveringPlace = covering.coveringPlace,
                            certificateNumber = covering.certificateNumber,
                            studCertificate = covering.studCertificate.toInput(),
                        ),
                        clock,
                    ),
                )
            is OverseasCoveringFixture ->
                recordOverseasCovering(
                    actor,
                    Command.now(
                        overseasCoveringCommand(
                            broodmareBreeding.id.value,
                            fixture.facts.stallion,
                            covering,
                        ),
                        clock,
                    ),
                )
        }

    /** 産駒を血統登録する。国内種付の産駒は内国産馬、海外種付の産駒は持込馬として登録する（#624）。 */
    private fun registerFoalOf(
        actor: Actor,
        synth: CoveredSynthesized,
        breedingResult: BreedingResult,
        foalFacts: FoalFacts,
        foalSynth: FoalSynthesized,
        clock: Clock,
    ): Result<RegisteredBloodHorse, Any> {
        val sex = Sex.valueOf(foalFacts.sex)
        val coatColor = CoatColor.valueOf(foalFacts.coatColor)
        val breedType = BreedType.valueOf(foalFacts.breedType)
        val dnaParentage = DnaParentageResult.valueOf(foalSynth.dnaParentage)
        return when (synth.covering) {
            is DomesticCoveringFixture ->
                registerFoal(
                    actor,
                    Command.now(
                        RegisterFoalCommand(
                            breedingResultId = breedingResult.id.value,
                            sex = sex,
                            coatColor = coatColor,
                            breedType = breedType,
                            breeder = foalFacts.breeder,
                            microchipNumber = foalSynth.microchipNumber,
                            dnaParentage = dnaParentage,
                            registrationNumber = foalSynth.pedigreeRegistrationNumber,
                        ),
                        clock,
                    ),
                )
            is OverseasCoveringFixture ->
                registerBroughtInFoal(
                    actor,
                    Command.now(
                        RegisterBroughtInFoalCommand(
                            breedingResultId = breedingResult.id.value,
                            sex = sex,
                            coatColor = coatColor,
                            breedType = breedType,
                            breeder = foalFacts.breeder,
                            microchipNumber = foalSynth.microchipNumber,
                            dnaParentage = dnaParentage,
                            registrationNumber = foalSynth.pedigreeRegistrationNumber,
                        ),
                        clock,
                    ),
                )
        }
    }

    /**
     * 種付を行わなかった年の経路: 牝馬 seed → 繁殖登録 → 種付せず記録 → 繁殖成績報告。
     *
     * RecordUncovered が起こす繁殖成績は生成時点で outcome = NotCovered が確定した終端レコードなので、
     * 出生報告（ReportFoaling）は通さない。種牡馬・種付証明書・種付成績報告・産駒はそもそも存在しない。
     */
    private fun runUncovered(actor: Actor, fixture: UncoveredSeasonFixture): HorseReplayOutcome {
        val session = ReplaySession(fixture)
        val facts = fixture.facts
        val synth = fixture.synthesized
        // 提出以外のドメイン日付は論理に効かないので、シーズン中の固定 Instant を使う。
        // 繁殖年は公開事実ではなく表示年からの換算（合成値）なので synthesized 側から取る。
        val neutralInstant: Instant = submissionInstant(LocalDate.of(synth.breedingYear, 4, 1))

        // 0. 繁殖牝馬を seed（産地が日本なら移行取り込み、外国産なら輸入馬経路。#633）。
        val broodmare =
            session.step(
                ReplayStep.REGISTER_BROODMARE,
                seedHorse(actor, facts.broodmare, synth.broodmare, seasonClock(neutralInstant)),
            ) ?: return session.stop()

        // 1. 繁殖登録（雌のみ）。
        val broodmareBreeding =
            session.step(
                ReplayStep.REGISTER_BROODMARE_BREEDING,
                registerBreedingRegistration(
                    actor,
                    Command.now(
                        RegisterBreedingRegistrationCommand(
                            broodmare.bloodHorse.id.value,
                            synth.broodmare.breedingRegistrationNumber,
                        ),
                        seasonClock(neutralInstant),
                    ),
                ),
            ) ?: return session.stop()

        // 2. 種付せず記録（この時点で outcome = NotCovered が確定した終端の繁殖成績が起きる）。
        val breedingResult =
            session.step(
                ReplayStep.RECORD_UNCOVERED,
                recordUncovered(
                    actor,
                    Command.now(
                        RecordUncoveredCommand(
                            breedingRegistrationId = broodmareBreeding.id.value,
                            breedingYear = Year.of(synth.breedingYear),
                        ),
                        seasonClock(neutralInstant),
                    ),
                ),
            ) ?: return session.stop()

        // 3. 繁殖成績報告（雌側・翌年 5/31 期限）。
        session.step(
            ReplayStep.SUBMIT_BREEDING_REPORT,
            submitBreedingReport(
                actor,
                Command.now(
                    SubmitBreedingReportCommand(breedingResult.id.value),
                    seasonClock(
                        submissionInstant(
                            LocalDate.parse(synth.submissions.breedingReportSubmittedOn)
                        )
                    ),
                ),
            ),
        ) ?: return session.stop()

        return session.complete()
    }
}

/**
 * 1 頭ぶんの replay 実行状態。段階の記録と、停止・完了の観測の組み立てを引き受ける。
 *
 * 種付ありの年（[com.example.api.replay.fixture.CoveredSeasonFixture]）と種付なしの年で経路が分かれるため、 経路をまたいで共有する。Err
 * はハーネスの失敗ではなく「発見」なので、例外にせず [StepResult] に記録する。
 */
private class ReplaySession(private val fixture: HorseFixture) {
    private val steps = mutableListOf<StepResult>()

    /**
     * 1 段階を実行結果から記録する。
     *
     * kotlin-result 2.x の Result は inline value class（Ok/Err は判別できるサブクラスではなく生成関数）なので、 is 分岐ではなく
     * fold で分解する。Err なら null を返し、呼び出し側は [stop] で打ち切る。
     */
    fun <V, E> step(name: ReplayStep, result: Result<V, E>): V? =
        result.fold(
            success = { value ->
                steps.add(StepResult(name, true, "ok"))
                value
            },
            failure = { error ->
                steps.add(StepResult(name, false, error.toString()))
                null
            },
        )

    /** 直前に記録した段階で停止した観測を返す（＝モデルが実在馬を弾いた＝発見）。 */
    fun stop(): HorseReplayOutcome = outcome(steps.last().step, steps.last().detail)

    /** 最後まで一周した観測を返す。 */
    fun complete(): HorseReplayOutcome = outcome(null, null)

    private fun outcome(stoppedAt: ReplayStep?, stopReason: String?): HorseReplayOutcome =
        HorseReplayOutcome(
            fixture.name,
            fixture.sources.toSourceRefs(),
            fixture.notes,
            steps.toList(),
            stoppedAt,
            stopReason,
        )
}
