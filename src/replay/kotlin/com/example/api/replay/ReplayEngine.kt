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
import com.example.api.application.studbook.horse.RegisterImportedHorseCommand
import com.example.api.application.studbook.horse.RegisterImportedHorseUseCase
import com.example.api.domain.shared.Command
import com.example.api.domain.studbook.model.horse.bloodhorse.BreedType
import com.example.api.domain.studbook.model.horse.bloodhorse.CoatColor
import com.example.api.domain.studbook.model.horse.bloodhorse.Sex
import com.example.api.domain.studbook.model.inspection.DnaParentageResult
import com.example.api.replay.fixture.FoundationHorse
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
        // 提出以外のドメイン日付は論理に効かないので、シーズン中の固定 Instant を使う。
        val neutralInstant: Instant = submissionInstant(LocalDate.of(fixture.coveringYear, 4, 1))

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
                fixture.sourceUrl,
                steps.toList(),
                at,
                steps.last().detail,
            )

        // 0. 基礎馬（種牡馬・繁殖牝馬）を輸入馬経路で seed（親不在で登録可）。
        val stallion =
            step(
                ReplayStep.REGISTER_STALLION,
                registerImportedHorse(
                    Command.now(importedCommand(fixture.stallion), seasonClock(neutralInstant))
                ),
            ) ?: return stop(ReplayStep.REGISTER_STALLION)
        val broodmare =
            step(
                ReplayStep.REGISTER_BROODMARE,
                registerImportedHorse(
                    Command.now(importedCommand(fixture.broodmare), seasonClock(neutralInstant))
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
                            fixture.stallion.breedingRegistrationNumber,
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
                            fixture.broodmare.breedingRegistrationNumber,
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
                            coveringDate = LocalDate.parse(fixture.covering.coveringDate),
                            coveringPlace = fixture.covering.coveringPlace,
                            certificateNumber = fixture.covering.certificateNumber,
                            studCertificate = fixture.covering.studCertificate.toInput(),
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
                    SubmitCoveringReportCommand(stallionBreeding.id.value, fixture.coveringYear),
                    seasonClock(
                        submissionInstant(LocalDate.parse(fixture.covering.reportSubmittedOn))
                    ),
                )
            ),
        ) ?: return stop(ReplayStep.SUBMIT_COVERING_REPORT)

        // 4. 出生報告。
        step(
            ReplayStep.REPORT_FOALING,
            reportFoaling(
                Command.now(
                    ReportFoalingCommand(breedingResult.id.value, fixture.foaling.toOutcome()),
                    seasonClock(neutralInstant),
                )
            ),
        ) ?: return stop(ReplayStep.REPORT_FOALING)

        // 5. 産駒血統登録 → 6. 馬名登録（LiveFoal かつ foal 情報があるときのみ）。
        val foal = fixture.foal
        if (foal != null) {
            val registeredFoal =
                step(
                    ReplayStep.REGISTER_FOAL,
                    registerFoal(
                        Command.now(
                            RegisterFoalCommand(
                                breedingResultId = breedingResult.id.value,
                                sex = Sex.valueOf(foal.sex),
                                coatColor = CoatColor.valueOf(foal.coatColor),
                                breedType = BreedType.valueOf(foal.breedType),
                                breeder = foal.breeder,
                                microchipNumber = foal.microchipNumber,
                                dnaParentage = DnaParentageResult.valueOf(foal.dnaParentage),
                                registrationNumber = foal.pedigreeRegistrationNumber,
                            ),
                            seasonClock(neutralInstant),
                        )
                    ),
                ) ?: return stop(ReplayStep.REGISTER_FOAL)

            step(
                ReplayStep.NAME_FOAL,
                nameHorse(
                    Command.now(
                        NameHorseCommand(registeredFoal.bloodHorse.id.value, foal.name),
                        seasonClock(neutralInstant),
                    )
                ),
            ) ?: return stop(ReplayStep.NAME_FOAL)
        }

        // 7. 繁殖成績報告（雌側・翌年 5/31 期限）。
        step(
            ReplayStep.SUBMIT_BREEDING_REPORT,
            submitBreedingReport(
                Command.now(
                    SubmitBreedingReportCommand(breedingResult.id.value),
                    seasonClock(
                        submissionInstant(LocalDate.parse(fixture.breedingReportSubmittedOn))
                    ),
                )
            ),
        ) ?: return stop(ReplayStep.SUBMIT_BREEDING_REPORT)

        return HorseReplayOutcome(fixture.name, fixture.sourceUrl, steps.toList(), null, null)
    }

    private fun importedCommand(h: FoundationHorse): RegisterImportedHorseCommand =
        RegisterImportedHorseCommand(
            sex = Sex.valueOf(h.sex),
            coatColor = CoatColor.valueOf(h.coatColor),
            breedType = BreedType.valueOf(h.breedType),
            dateOfBirth = LocalDate.parse(h.dateOfBirth),
            breeder = h.breeder,
            microchipNumber = h.microchipNumber,
            originCountry = h.originCountry,
            landingDate = LocalDate.parse(h.landingDate),
            registrationNumber = h.pedigreeRegistrationNumber,
        )
}
