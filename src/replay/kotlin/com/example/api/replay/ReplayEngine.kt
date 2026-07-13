package com.example.api.replay

import com.example.api.application.studbook.breeding.RecordCoveringCommand
import com.example.api.application.studbook.breeding.RecordCoveringUseCase
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
import com.example.api.application.studbook.horse.RegisterFoalCommand
import com.example.api.application.studbook.horse.RegisterFoalUseCase
import com.example.api.application.studbook.horse.RegisterImportedHorseUseCase
import com.example.api.domain.shared.Command
import com.example.api.domain.studbook.model.horse.bloodhorse.BreedType
import com.example.api.domain.studbook.model.horse.bloodhorse.CoatColor
import com.example.api.domain.studbook.model.horse.bloodhorse.Sex
import com.example.api.domain.studbook.model.inspection.DnaParentageResult
import com.example.api.replay.fixture.CoveredSeasonFixture
import com.example.api.replay.fixture.HorseFixture
import com.example.api.replay.fixture.UncoveredSeasonFixture
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.fold
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
    private val registerBreedingRegistration: RegisterBreedingRegistrationUseCase,
    private val recordCovering: RecordCoveringUseCase,
    private val recordUncovered: RecordUncoveredUseCase,
    private val submitCoveringReport: SubmitCoveringReportUseCase,
    private val reportFoaling: ReportFoalingUseCase,
    private val registerFoal: RegisterFoalUseCase,
    private val nameHorse: NameHorseUseCase,
    private val submitBreedingReport: SubmitBreedingReportUseCase,
) {
    fun run(fixture: HorseFixture): HorseReplayOutcome =
        when (fixture) {
            is CoveredSeasonFixture -> runCovered(fixture)
            is UncoveredSeasonFixture -> runUncovered(fixture)
        }

    /** 種付を行った年の経路: seed ×2 → 繁殖登録 ×2 → 種付 → 種付成績報告 → 出生報告 → 産駒登録 → 馬名登録 → 繁殖成績報告。 */
    private fun runCovered(fixture: CoveredSeasonFixture): HorseReplayOutcome {
        val session = ReplaySession(fixture)
        val facts = fixture.facts
        val synth = fixture.synthesized
        // 提出以外のドメイン日付は論理に効かないので、シーズン中の固定 Instant を使う。
        val neutralInstant: Instant = submissionInstant(LocalDate.of(facts.coveringYear, 4, 1))

        // 0. 基礎馬（種牡馬・繁殖牝馬）を輸入馬経路で seed（親不在で登録可）。
        //    内国産馬も現状これしか経路がないため、合成した出生国・輸入年月日で通す。
        val stallion =
            session.step(
                ReplayStep.REGISTER_STALLION,
                registerImportedHorse(
                    Command.now(
                        importedCommand(facts.stallion, synth.stallion),
                        seasonClock(neutralInstant),
                    )
                ),
            ) ?: return session.stop()
        val broodmare =
            session.step(
                ReplayStep.REGISTER_BROODMARE,
                registerImportedHorse(
                    Command.now(
                        importedCommand(facts.broodmare, synth.broodmare),
                        seasonClock(neutralInstant),
                    )
                ),
            ) ?: return session.stop()

        // 1. 繁殖登録（雄・雌）。
        val stallionBreeding =
            session.step(
                ReplayStep.REGISTER_STALLION_BREEDING,
                registerBreedingRegistration(
                    Command.now(
                        RegisterBreedingRegistrationCommand(
                            stallion.bloodHorse.id.value,
                            synth.stallion.breedingRegistrationNumber,
                        ),
                        seasonClock(neutralInstant),
                    )
                ),
            ) ?: return session.stop()
        val broodmareBreeding =
            session.step(
                ReplayStep.REGISTER_BROODMARE_BREEDING,
                registerBreedingRegistration(
                    Command.now(
                        RegisterBreedingRegistrationCommand(
                            broodmare.bloodHorse.id.value,
                            synth.broodmare.breedingRegistrationNumber,
                        ),
                        seasonClock(neutralInstant),
                    )
                ),
            ) ?: return session.stop()

        // 2. 種付記録。
        val breedingResult =
            session.step(
                ReplayStep.RECORD_COVERING,
                recordCovering(
                    Command.now(
                        RecordCoveringCommand(
                            breedingRegistrationId = broodmareBreeding.id.value,
                            stallionRegistrationId = stallionBreeding.id.value,
                            coveringDate = LocalDate.parse(synth.covering.coveringDate),
                            coveringPlace = synth.covering.coveringPlace,
                            certificateNumber = synth.covering.certificateNumber,
                            studCertificate = synth.covering.studCertificate.toInput(),
                        ),
                        seasonClock(neutralInstant),
                    )
                ),
            ) ?: return session.stop()

        // 3. 種付成績報告（雄側・当年 9/30 期限）。提出日を Command.issuedAt に反映。
        session.step(
            ReplayStep.SUBMIT_COVERING_REPORT,
            submitCoveringReport(
                Command.now(
                    SubmitCoveringReportCommand(stallionBreeding.id.value, facts.coveringYear),
                    seasonClock(
                        submissionInstant(
                            LocalDate.parse(synth.submissions.coveringReportSubmittedOn)
                        )
                    ),
                )
            ),
        ) ?: return session.stop()

        // 4. 出生報告。
        session.step(
            ReplayStep.REPORT_FOALING,
            reportFoaling(
                Command.now(
                    ReportFoalingCommand(breedingResult.id.value, facts.foaling.toOutcome()),
                    seasonClock(neutralInstant),
                )
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
                    registerFoal(
                        Command.now(
                            RegisterFoalCommand(
                                breedingResultId = breedingResult.id.value,
                                sex = Sex.valueOf(foalFacts.sex),
                                coatColor = CoatColor.valueOf(foalFacts.coatColor),
                                breedType = BreedType.valueOf(foalFacts.breedType),
                                breeder = foalFacts.breeder,
                                microchipNumber = foalSynth.microchipNumber,
                                dnaParentage = DnaParentageResult.valueOf(foalSynth.dnaParentage),
                                registrationNumber = foalSynth.pedigreeRegistrationNumber,
                            ),
                            seasonClock(neutralInstant),
                        )
                    ),
                ) ?: return session.stop()

            val foalName = foalFacts.name
            if (foalName != null) {
                session.step(
                    ReplayStep.NAME_FOAL,
                    nameHorse(
                        Command.now(
                            NameHorseCommand(registeredFoal.bloodHorse.id.value, foalName),
                            seasonClock(neutralInstant),
                        )
                    ),
                ) ?: return session.stop()
            }
        }

        // 7. 繁殖成績報告（雌側・翌年 5/31 期限）。
        session.step(
            ReplayStep.SUBMIT_BREEDING_REPORT,
            submitBreedingReport(
                Command.now(
                    SubmitBreedingReportCommand(breedingResult.id.value),
                    seasonClock(
                        submissionInstant(
                            LocalDate.parse(synth.submissions.breedingReportSubmittedOn)
                        )
                    ),
                )
            ),
        ) ?: return session.stop()

        return session.complete()
    }

    /**
     * 種付を行わなかった年の経路: 牝馬 seed → 繁殖登録 → 種付せず記録 → 繁殖成績報告。
     *
     * RecordUncovered が起こす繁殖成績は生成時点で outcome = NotCovered が確定した終端レコードなので、
     * 出生報告（ReportFoaling）は通さない。種牡馬・種付証明書・種付成績報告・産駒はそもそも存在しない。
     */
    private fun runUncovered(fixture: UncoveredSeasonFixture): HorseReplayOutcome {
        val session = ReplaySession(fixture)
        val facts = fixture.facts
        val synth = fixture.synthesized
        // 提出以外のドメイン日付は論理に効かないので、シーズン中の固定 Instant を使う。
        val neutralInstant: Instant = submissionInstant(LocalDate.of(facts.breedingYear, 4, 1))

        // 0. 繁殖牝馬を輸入馬経路で seed（内国産だが現状これしか経路がない。#633）。
        val broodmare =
            session.step(
                ReplayStep.REGISTER_BROODMARE,
                registerImportedHorse(
                    Command.now(
                        importedCommand(facts.broodmare, synth.broodmare),
                        seasonClock(neutralInstant),
                    )
                ),
            ) ?: return session.stop()

        // 1. 繁殖登録（雌のみ）。
        val broodmareBreeding =
            session.step(
                ReplayStep.REGISTER_BROODMARE_BREEDING,
                registerBreedingRegistration(
                    Command.now(
                        RegisterBreedingRegistrationCommand(
                            broodmare.bloodHorse.id.value,
                            synth.broodmare.breedingRegistrationNumber,
                        ),
                        seasonClock(neutralInstant),
                    )
                ),
            ) ?: return session.stop()

        // 2. 種付せず記録（この時点で outcome = NotCovered が確定した終端の繁殖成績が起きる）。
        val breedingResult =
            session.step(
                ReplayStep.RECORD_UNCOVERED,
                recordUncovered(
                    Command.now(
                        RecordUncoveredCommand(
                            breedingRegistrationId = broodmareBreeding.id.value,
                            breedingYear = Year.of(facts.breedingYear),
                        ),
                        seasonClock(neutralInstant),
                    )
                ),
            ) ?: return session.stop()

        // 3. 繁殖成績報告（雌側・翌年 5/31 期限）。
        session.step(
            ReplayStep.SUBMIT_BREEDING_REPORT,
            submitBreedingReport(
                Command.now(
                    SubmitBreedingReportCommand(breedingResult.id.value),
                    seasonClock(
                        submissionInstant(
                            LocalDate.parse(synth.submissions.breedingReportSubmittedOn)
                        )
                    ),
                )
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
