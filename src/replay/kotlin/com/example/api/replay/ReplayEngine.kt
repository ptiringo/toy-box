package com.example.api.replay

import com.example.api.application.studbook.breeding.RecordCoveringCommand
import com.example.api.application.studbook.breeding.RecordCoveringUseCase
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
import com.example.api.replay.fixture.HorseFixture
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.fold
import java.time.Instant
import java.time.LocalDate
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
    private val submitCoveringReport: SubmitCoveringReportUseCase,
    private val reportFoaling: ReportFoalingUseCase,
    private val registerFoal: RegisterFoalUseCase,
    private val nameHorse: NameHorseUseCase,
    private val submitBreedingReport: SubmitBreedingReportUseCase,
) {
    fun run(fixture: HorseFixture): HorseReplayOutcome {
        val steps = mutableListOf<StepResult>()
        val facts = fixture.facts
        val synth = fixture.synthesized
        // 提出以外のドメイン日付は論理に効かないので、シーズン中の固定 Instant を使う。
        val neutralInstant: Instant = submissionInstant(LocalDate.of(facts.coveringYear, 4, 1))

        // 各段階を実行するローカルヘルパ。Err なら steps に記録し stop 用の理由文字列を返す。
        // kotlin-result 2.x の Result は inline value class（Ok/Err は判別できるサブクラスではなく生成関数）なので、
        // is 分岐ではなく fold で分解する。
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

        fun stop(at: ReplayStep) =
            HorseReplayOutcome(
                fixture.name,
                fixture.sources.breedingRecord,
                synth.notes,
                steps.toList(),
                at,
                steps.last().detail,
            )

        // 0. 基礎馬（種牡馬・繁殖牝馬）を輸入馬経路で seed（親不在で登録可）。
        //    内国産馬も現状これしか経路がないため、合成した出生国・輸入年月日で通す。
        val stallion =
            step(
                ReplayStep.REGISTER_STALLION,
                registerImportedHorse(
                    Command.now(
                        importedCommand(facts.stallion, synth.stallion),
                        seasonClock(neutralInstant),
                    )
                ),
            ) ?: return stop(ReplayStep.REGISTER_STALLION)
        val broodmare =
            step(
                ReplayStep.REGISTER_BROODMARE,
                registerImportedHorse(
                    Command.now(
                        importedCommand(facts.broodmare, synth.broodmare),
                        seasonClock(neutralInstant),
                    )
                ),
            ) ?: return stop(ReplayStep.REGISTER_BROODMARE)

        // 1. 繁殖登録（雄・雌）。
        val stallionBreeding =
            step(
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
            ) ?: return stop(ReplayStep.REGISTER_STALLION_BREEDING)
        val broodmareBreeding =
            step(
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
            ) ?: return stop(ReplayStep.REGISTER_BROODMARE_BREEDING)

        // 2. 種付記録。
        val breedingResult =
            step(
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
            ) ?: return stop(ReplayStep.RECORD_COVERING)

        // 3. 種付成績報告（雄側・当年 9/30 期限）。提出日を Command.issuedAt に反映。
        step(
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
        ) ?: return stop(ReplayStep.SUBMIT_COVERING_REPORT)

        // 4. 出生報告。
        step(
            ReplayStep.REPORT_FOALING,
            reportFoaling(
                Command.now(
                    ReportFoalingCommand(breedingResult.id.value, facts.foaling.toOutcome()),
                    seasonClock(neutralInstant),
                )
            ),
        ) ?: return stop(ReplayStep.REPORT_FOALING)

        // 5. 産駒血統登録 → 6. 馬名登録（LiveFoal かつ産駒情報があるときのみ）。
        //    未命名の産駒（JBIS 上も馬名が付いていない）は馬名登録を行わない。
        val foalFacts = facts.foal
        val foalSynth = synth.foal
        if (foalFacts != null && foalSynth != null) {
            val registeredFoal =
                step(
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
                ) ?: return stop(ReplayStep.REGISTER_FOAL)

            val foalName = foalFacts.name
            if (foalName != null) {
                step(
                    ReplayStep.NAME_FOAL,
                    nameHorse(
                        Command.now(
                            NameHorseCommand(registeredFoal.bloodHorse.id.value, foalName),
                            seasonClock(neutralInstant),
                        )
                    ),
                ) ?: return stop(ReplayStep.NAME_FOAL)
            }
        }

        // 7. 繁殖成績報告（雌側・翌年 5/31 期限）。
        step(
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
        ) ?: return stop(ReplayStep.SUBMIT_BREEDING_REPORT)

        return HorseReplayOutcome(
            fixture.name,
            fixture.sources.breedingRecord,
            synth.notes,
            steps.toList(),
            null,
            null,
        )
    }
}
