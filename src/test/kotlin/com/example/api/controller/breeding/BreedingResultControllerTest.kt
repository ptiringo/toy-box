package com.example.api.controller.breeding

import com.example.api.application.iam.world.WorldQueries
import com.example.api.application.studbook.breeding.BreedingResultDetailView
import com.example.api.application.studbook.breeding.BreedingResultNotFound
import com.example.api.application.studbook.breeding.GetBreedingResultQuery
import com.example.api.application.studbook.breeding.GetBreedingResultUseCase
import com.example.api.application.studbook.breeding.RecordCoveringCommand
import com.example.api.application.studbook.breeding.RecordCoveringUseCase
import com.example.api.application.studbook.breeding.RecordCoveringUseCaseError
import com.example.api.application.studbook.breeding.RecordUncoveredCommand
import com.example.api.application.studbook.breeding.RecordUncoveredUseCase
import com.example.api.application.studbook.breeding.RecordUncoveredUseCaseError
import com.example.api.application.studbook.breeding.ReportFoalingCommand
import com.example.api.application.studbook.breeding.ReportFoalingUseCase
import com.example.api.application.studbook.breeding.ReportFoalingUseCaseError
import com.example.api.application.studbook.breeding.SubmitBreedingReportCommand
import com.example.api.application.studbook.breeding.SubmitBreedingReportUseCase
import com.example.api.application.studbook.breeding.SubmitBreedingReportUseCaseError
import com.example.api.config.ClockConfiguration
import com.example.api.controller.ActorArgumentResolver
import com.example.api.domain.iam.model.account.AccountRepository
import com.example.api.domain.shared.AccountId
import com.example.api.domain.shared.Actor
import com.example.api.domain.shared.Command
import com.example.api.domain.shared.WorldId
import com.example.api.domain.shared.generateId
import com.example.api.domain.studbook.model.breeding.BreedingFixture
import com.example.api.domain.studbook.model.breeding.BreedingRegion
import com.example.api.domain.studbook.model.breeding.CoveringValidityError
import com.example.api.domain.studbook.model.breeding.FoalingOutcome
import com.example.api.domain.studbook.model.breeding.RecordCoveringError
import com.example.api.domain.studbook.model.breeding.RecordUncoveredError
import com.example.api.domain.studbook.model.breeding.SubmitBreedingReportError
import com.example.api.domain.studbook.model.breeding.ValidityPeriod
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import java.time.LocalDate
import java.time.Year
import java.util.UUID
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.TestConstructor
import org.springframework.test.context.TestConstructor.AutowireMode
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.assertj.MockMvcTester

@Execution(ExecutionMode.SAME_THREAD)
@WebMvcTest(BreedingResultController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ClockConfiguration::class)
@TestConstructor(autowireMode = AutowireMode.ALL)
class BreedingResultControllerTest(val mockMvc: MockMvc) {
    @MockkBean private lateinit var recordCovering: RecordCoveringUseCase
    @MockkBean private lateinit var recordUncovered: RecordUncoveredUseCase
    @MockkBean private lateinit var reportFoaling: ReportFoalingUseCase
    @MockkBean private lateinit var submitReport: SubmitBreedingReportUseCase
    @MockkBean private lateinit var getBreedingResult: GetBreedingResultUseCase

    // WebMvcConfig（CurrentAccountArgumentResolver）が全 @WebMvcTest スライスへ自動で載るため必要（本テストの検証対象ではない）。
    @MockkBean private lateinit var accounts: AccountRepository
    @MockkBean private lateinit var worldQueries: WorldQueries

    @MockkBean private lateinit var actorArgumentResolver: ActorArgumentResolver

    private val actor = Actor(accountId = AccountId(generateId()), worldId = WorldId(generateId()))

    /** パスに載せる世界のID。resolver はモックのため値は解決に使われない。 */
    private val worldId = actor.worldId.value

    /**
     * `WebMvcConfig` は `WebMvcConfigurer` なので `ActorArgumentResolver` は全スライスに載る。slice は認証フィルタを
     * 無効化しているため実解決は走らせず、固定の [actor] を返すよう差し替える（#704）。
     */
    @BeforeEach
    fun stubActor() {
        every { actorArgumentResolver.supportsParameter(any()) } returns true
        every { actorArgumentResolver.resolveArgument(any(), any(), any(), any()) } returns actor
    }

    private val tester = MockMvcTester.create(mockMvc)

    @Nested
    inner class RecordCoveringCase {
        private val uri = "/api/worlds/{worldId}/breedingResults"

        /** デシリアライズに通る正しい種付記録リクエストボディ。ユースケースはモックのため中身の整合は問われない。 */
        private val validBody =
            """
            {
                "breeding_registration_id": "11111111-1111-1111-1111-111111111111",
                "covering": {
                    "stallion_registration_id": "22222222-2222-2222-2222-222222222222",
                    "covering_date": "2024-04-01",
                    "covering_place": "北海道",
                    "certificate_number": "C-2024-0001",
                    "stud_certificate": {
                        "number": "S-2024-0001",
                        "valid_regions": ["北海道"],
                        "valid_period_start": "2024-01-01",
                        "valid_period_end": "2024-12-31"
                    }
                }
            }
            """
                .trimIndent()

        @Test
        fun `正常な入力で 201 Created と起票された繁殖成績が返ること`() {
            val saved = BreedingFixture.breedingResult()
            every { recordCovering(any<Actor>(), any<Command<RecordCoveringCommand>>()) } returns
                Ok(saved)

            tester
                .post()
                .uri(uri, worldId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody)
                .assertThat()
                .hasStatus(HttpStatus.CREATED)
                .bodyJson()
                .extractingPath("$.covering_place")
                .isEqualTo("北海道")
        }

        @Test
        fun `InvalidCertificateNumber で 400 と problem+json が返ること`() {
            every { recordCovering(any<Actor>(), any<Command<RecordCoveringCommand>>()) } returns
                Err(RecordCoveringUseCaseError.InvalidCertificateNumber)

            tester
                .post()
                .uri(uri, worldId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody)
                .assertThat()
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.error_code")
                .isEqualTo("invalid-covering-certificate-number")
        }

        @Test
        fun `InvalidStudCertificateNumber で 400 と problem+json が返ること`() {
            every { recordCovering(any<Actor>(), any<Command<RecordCoveringCommand>>()) } returns
                Err(RecordCoveringUseCaseError.InvalidStudCertificateNumber)

            tester
                .post()
                .uri(uri, worldId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody)
                .assertThat()
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.error_code")
                .isEqualTo("invalid-stud-certificate-number")
        }

        @Test
        fun `BreedingRegistrationNotFound で 422 と breedingRegistrationId 付きの problem+json が返ること`() {
            val id = UUID.fromString("11111111-1111-1111-1111-111111111111")
            every { recordCovering(any<Actor>(), any<Command<RecordCoveringCommand>>()) } returns
                Err(RecordCoveringUseCaseError.BreedingRegistrationNotFound(id))

            tester
                .post()
                .uri(uri, worldId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody)
                .assertThat()
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.breeding_registration_id")
                .isEqualTo(id.toString())
        }

        @Test
        fun `StallionRegistrationNotFound で 422 と stallionRegistrationId 付きの problem+json が返ること`() {
            val id = UUID.fromString("22222222-2222-2222-2222-222222222222")
            every { recordCovering(any<Actor>(), any<Command<RecordCoveringCommand>>()) } returns
                Err(RecordCoveringUseCaseError.StallionRegistrationNotFound(id))

            tester
                .post()
                .uri(uri, worldId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody)
                .assertThat()
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.stallion_registration_id")
                .isEqualTo(id.toString())
        }

        @Test
        fun `前提条件違反（NotStallion）が 422 と problem+json に変換されること`() {
            every { recordCovering(any<Actor>(), any<Command<RecordCoveringCommand>>()) } returns
                Err(
                    RecordCoveringUseCaseError.PreconditionViolated(RecordCoveringError.NotStallion)
                )

            tester
                .post()
                .uri(uri, worldId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody)
                .assertThat()
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.error_code")
                .isEqualTo("not-stallion")
        }

        @Test
        fun `前提条件違反（NotBroodmare）が 422 と problem+json に変換されること`() {
            every { recordCovering(any<Actor>(), any<Command<RecordCoveringCommand>>()) } returns
                Err(
                    RecordCoveringUseCaseError.PreconditionViolated(
                        RecordCoveringError.NotBroodmare
                    )
                )

            tester
                .post()
                .uri(uri, worldId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody)
                .assertThat()
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.error_code")
                .isEqualTo("not-broodmare")
        }

        @Test
        fun `有効期間外（InvalidCovering OutsideValidPeriod）が 422 と problem+json に変換されること`() {
            val period =
                ValidityPeriod.create(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 3, 31)).unwrap()
            every { recordCovering(any<Actor>(), any<Command<RecordCoveringCommand>>()) } returns
                Err(
                    RecordCoveringUseCaseError.PreconditionViolated(
                        RecordCoveringError.InvalidCovering(
                            CoveringValidityError.OutsideValidPeriod(
                                LocalDate.of(2024, 4, 1),
                                period,
                            )
                        )
                    )
                )

            tester
                .post()
                .uri(uri, worldId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody)
                .assertThat()
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.error_code")
                .isEqualTo("covering-outside-valid-period")
        }

        @Test
        fun `有効区域外（InvalidCovering OutsideValidRegion）が 422 と problem+json に変換されること`() {
            val hokkaido = BreedingRegion.create("北海道").unwrap()
            val aomori = BreedingRegion.create("青森").unwrap()
            every { recordCovering(any<Actor>(), any<Command<RecordCoveringCommand>>()) } returns
                Err(
                    RecordCoveringUseCaseError.PreconditionViolated(
                        RecordCoveringError.InvalidCovering(
                            CoveringValidityError.OutsideValidRegion(aomori, setOf(hokkaido))
                        )
                    )
                )

            tester
                .post()
                .uri(uri, worldId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody)
                .assertThat()
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.error_code")
                .isEqualTo("covering-outside-valid-region")
        }

        @Test
        fun `重複記録（AlreadyRecordedForYear）が 409 と繁殖年つきの problem+json に変換されること`() {
            val existing = BreedingFixture.breedingResult()
            every { recordCovering(any<Actor>(), any<Command<RecordCoveringCommand>>()) } returns
                Err(
                    RecordCoveringUseCaseError.PreconditionViolated(
                        RecordCoveringError.AlreadyRecordedForYear(Year.of(2024), existing.id)
                    )
                )

            tester
                .post()
                .uri(uri, worldId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody)
                .assertThat()
                .hasStatus(HttpStatus.CONFLICT)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.breeding_year")
                .isEqualTo(2024)
        }
    }

    @Nested
    inner class RecordUncoveredCase {
        private val uri = "/api/worlds/{worldId}/breedingResults"

        /** covering を持たない（種付せず）正しいリクエストボディ。 */
        private val validBody =
            """
            {
                "breeding_registration_id": "11111111-1111-1111-1111-111111111111",
                "breeding_year": 2024
            }
            """
                .trimIndent()

        @Test
        fun `covering 無しの入力で 201 Created と種付せずの繁殖成績が返ること`() {
            val saved = BreedingFixture.uncoveredBreedingResult()
            every { recordUncovered(any<Actor>(), any<Command<RecordUncoveredCommand>>()) } returns
                Ok(saved)

            tester
                .post()
                .uri(uri, worldId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody)
                .assertThat()
                .hasStatus(HttpStatus.CREATED)
                .bodyJson()
                .extractingPath("$.outcome.kind")
                .isEqualTo("NOT_COVERED")
        }

        @Test
        fun `covering 無しなのに breeding_year が欠けると 400 と problem+json が返ること`() {
            tester
                .post()
                .uri(uri, worldId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{ "breeding_registration_id": "11111111-1111-1111-1111-111111111111" }"""
                )
                .assertThat()
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.error_code")
                .isEqualTo("missing-breeding-year")
        }

        @Test
        fun `BreedingRegistrationNotFound で 422 と breedingRegistrationId 付きの problem+json が返ること`() {
            val id = UUID.fromString("11111111-1111-1111-1111-111111111111")
            every { recordUncovered(any<Actor>(), any<Command<RecordUncoveredCommand>>()) } returns
                Err(RecordUncoveredUseCaseError.BreedingRegistrationNotFound(id))

            tester
                .post()
                .uri(uri, worldId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody)
                .assertThat()
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.breeding_registration_id")
                .isEqualTo(id.toString())
        }

        @Test
        fun `前提条件違反（NotBroodmare）が 422 と problem+json に変換されること`() {
            every { recordUncovered(any<Actor>(), any<Command<RecordUncoveredCommand>>()) } returns
                Err(
                    RecordUncoveredUseCaseError.PreconditionViolated(
                        RecordUncoveredError.NotBroodmare
                    )
                )

            tester
                .post()
                .uri(uri, worldId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody)
                .assertThat()
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.error_code")
                .isEqualTo("not-broodmare")
        }

        @Test
        fun `重複記録（AlreadyRecordedForYear）が 409 と繁殖年つきの problem+json に変換されること`() {
            val existing = BreedingFixture.uncoveredBreedingResult()
            every { recordUncovered(any<Actor>(), any<Command<RecordUncoveredCommand>>()) } returns
                Err(
                    RecordUncoveredUseCaseError.PreconditionViolated(
                        RecordUncoveredError.AlreadyRecordedForYear(Year.of(2024), existing.id)
                    )
                )

            tester
                .post()
                .uri(uri, worldId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody)
                .assertThat()
                .hasStatus(HttpStatus.CONFLICT)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.breeding_year")
                .isEqualTo(2024)
        }
    }

    @Nested
    inner class ReportFoalingCase {
        private val breedingResultId = "33333333-3333-3333-3333-333333333333"
        private val uri = "/api/worlds/{worldId}/breedingResults/{breedingResultId}:reportFoaling"
        private val liveFoalBody = """{ "outcome": "LIVE_FOAL", "foaling_date": "2025-03-20" }"""

        @Test
        fun `正常な入力で 200 OK と更新後の繁殖成績が返ること`() {
            val reported =
                BreedingFixture.breedingResult()
                    .recordFoaling(FoalingOutcome.LiveFoal(LocalDate.of(2025, 3, 20)))
                    .unwrap()
            every { reportFoaling(any<Actor>(), any<Command<ReportFoalingCommand>>()) } returns
                Ok(reported)

            tester
                .post()
                .uri(uri, worldId, breedingResultId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(liveFoalBody)
                .assertThat()
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .extractingPath("$.outcome.kind")
                .isEqualTo("LIVE_FOAL")
        }

        @Test
        fun `生産なのに分娩日が欠けていると 400 と problem+json が返ること`() {
            tester
                .post()
                .uri(uri, worldId, breedingResultId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{ "outcome": "LIVE_FOAL" }""")
                .assertThat()
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.error_code")
                .isEqualTo("missing-foaling-date")
        }

        @Test
        fun `BreedingResultNotFound で 404 と breedingResultId 付きの problem+json が返ること`() {
            val id = UUID.fromString(breedingResultId)
            every { reportFoaling(any<Actor>(), any<Command<ReportFoalingCommand>>()) } returns
                Err(ReportFoalingUseCaseError.BreedingResultNotFound(id))

            tester
                .post()
                .uri(uri, worldId, breedingResultId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(liveFoalBody)
                .assertThat()
                .hasStatus(HttpStatus.NOT_FOUND)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.breeding_result_id")
                .isEqualTo(id.toString())
        }

        @Test
        fun `AlreadyReported で 409 と problem+json が返ること`() {
            every { reportFoaling(any<Actor>(), any<Command<ReportFoalingCommand>>()) } returns
                Err(
                    ReportFoalingUseCaseError.AlreadyReported(
                        FoalingOutcome.LiveFoal(LocalDate.of(2025, 3, 20))
                    )
                )

            tester
                .post()
                .uri(uri, worldId, breedingResultId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{ "outcome": "NOT_CONCEIVED" }""")
                .assertThat()
                .hasStatus(HttpStatus.CONFLICT)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.error_code")
                .isEqualTo("foaling-already-recorded")
        }

        @Test
        fun `ConcurrentModification で 409 と problem+json が返ること`() {
            val id = UUID.fromString(breedingResultId)
            every { reportFoaling(any<Actor>(), any<Command<ReportFoalingCommand>>()) } returns
                Err(ReportFoalingUseCaseError.ConcurrentModification(id))

            tester
                .post()
                .uri(uri, worldId, breedingResultId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(liveFoalBody)
                .assertThat()
                .hasStatus(HttpStatus.CONFLICT)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.error_code")
                .isEqualTo("concurrent-modification")
        }
    }

    @Nested
    inner class SubmitReportCase {
        private val breedingResultId = "44444444-4444-4444-4444-444444444444"
        private val uri = "/api/worlds/{worldId}/breedingResults/{breedingResultId}:submitReport"

        /** 分娩結果確定済み・提出済み（2025-06-01 = 期限超過）の繁殖成績。 */
        private fun submittedResult() =
            BreedingFixture.breedingResult()
                .recordFoaling(FoalingOutcome.LiveFoal(LocalDate.of(2025, 3, 20)))
                .unwrap()
                .submitReport(LocalDate.of(2025, 6, 1))
                .unwrap()

        @Test
        fun `正常な提出で 200 OK と提出日・期限超過付きの繁殖成績が返ること`() {
            every {
                submitReport(any<Actor>(), any<Command<SubmitBreedingReportCommand>>())
            } returns Ok(submittedResult())

            tester
                .post()
                .uri(uri, worldId, breedingResultId)
                .assertThat()
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .extractingPath("$.report_submitted_on")
                .isEqualTo("2025-06-01")
        }

        @Test
        fun `期限超過の提出は report_submitted_late が true で返ること`() {
            every {
                submitReport(any<Actor>(), any<Command<SubmitBreedingReportCommand>>())
            } returns Ok(submittedResult())

            tester
                .post()
                .uri(uri, worldId, breedingResultId)
                .assertThat()
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .extractingPath("$.report_submitted_late")
                .isEqualTo(true)
        }

        @Test
        fun `BreedingResultNotFound で 404 と breeding_result_id 付きの problem+json が返ること`() {
            val id = UUID.fromString(breedingResultId)
            every {
                submitReport(any<Actor>(), any<Command<SubmitBreedingReportCommand>>())
            } returns Err(SubmitBreedingReportUseCaseError.BreedingResultNotFound(id))

            tester
                .post()
                .uri(uri, worldId, breedingResultId)
                .assertThat()
                .hasStatus(HttpStatus.NOT_FOUND)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.breeding_result_id")
                .isEqualTo(id.toString())
        }

        @Test
        fun `分娩結果未確定で 422 と problem+json が返ること`() {
            every {
                submitReport(any<Actor>(), any<Command<SubmitBreedingReportCommand>>())
            } returns
                Err(
                    SubmitBreedingReportUseCaseError.PreconditionViolated(
                        SubmitBreedingReportError.OutcomeNotRecorded
                    )
                )

            tester
                .post()
                .uri(uri, worldId, breedingResultId)
                .assertThat()
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.error_code")
                .isEqualTo("breeding-report-outcome-not-recorded")
        }

        @Test
        fun `提出済みで 409 と既存提出日付きの problem+json が返ること`() {
            every {
                submitReport(any<Actor>(), any<Command<SubmitBreedingReportCommand>>())
            } returns
                Err(
                    SubmitBreedingReportUseCaseError.PreconditionViolated(
                        SubmitBreedingReportError.ReportAlreadySubmitted(LocalDate.of(2025, 5, 1))
                    )
                )

            tester
                .post()
                .uri(uri, worldId, breedingResultId)
                .assertThat()
                .hasStatus(HttpStatus.CONFLICT)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.error_code")
                .isEqualTo("breeding-report-already-submitted")

            tester
                .post()
                .uri(uri, worldId, breedingResultId)
                .assertThat()
                .hasStatus(HttpStatus.CONFLICT)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.report_submitted_on")
                .isEqualTo("2025-05-01")
        }

        @Test
        fun `ConcurrentModification で 409 と problem+json が返ること`() {
            val id = UUID.fromString(breedingResultId)
            every {
                submitReport(any<Actor>(), any<Command<SubmitBreedingReportCommand>>())
            } returns Err(SubmitBreedingReportUseCaseError.ConcurrentModification(id))

            tester
                .post()
                .uri(uri, worldId, breedingResultId)
                .assertThat()
                .hasStatus(HttpStatus.CONFLICT)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.error_code")
                .isEqualTo("concurrent-modification")
        }
    }

    @Nested
    inner class GetCase {
        private val uri = "/api/worlds/{worldId}/breedingResults/{id}"
        private val id = UUID.fromString("44444444-4444-4444-4444-444444444444")

        @Test
        fun `存在する ID の照会で 200 と繁殖成績リソースが返ること`() {
            every { getBreedingResult(any<Actor>(), GetBreedingResultQuery(id)) } returns
                Ok(
                    BreedingResultDetailView(
                        id = id,
                        breedingRegistrationId = generateId(),
                        breedingYear = 2024,
                        stallionId = generateId(),
                        coveringDate = LocalDate.of(2024, 4, 1),
                        coveringPlace = "北海道",
                        certificateNumber = "C-2024-0001",
                        outcome = FoalingOutcome.LiveFoal(LocalDate.of(2025, 3, 20)),
                        // 繁殖年 2024 の期限は 2025-05-31。その翌日の提出は期限超過として応答に表れる。
                        reportSubmittedOn = LocalDate.of(2025, 6, 1),
                    )
                )

            tester
                .get()
                .uri(uri, worldId, id)
                .assertThat()
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.outcome.kind")
                .isEqualTo("LIVE_FOAL")
        }

        @Test
        fun `導出値の期限超過フラグが応答に載ること`() {
            every { getBreedingResult(any<Actor>(), GetBreedingResultQuery(id)) } returns
                Ok(
                    BreedingResultDetailView(
                        id = id,
                        breedingRegistrationId = generateId(),
                        breedingYear = 2024,
                        stallionId = generateId(),
                        coveringDate = LocalDate.of(2024, 4, 1),
                        coveringPlace = "北海道",
                        certificateNumber = "C-2024-0001",
                        outcome = FoalingOutcome.LiveFoal(LocalDate.of(2025, 3, 20)),
                        reportSubmittedOn = LocalDate.of(2025, 6, 1),
                    )
                )

            tester
                .get()
                .uri(uri, worldId, id)
                .assertThat()
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.report_submitted_late")
                .isEqualTo(true)
        }

        @Test
        fun `照会対象が不在なら 404 と problem+json が返ること`() {
            every { getBreedingResult(any<Actor>(), GetBreedingResultQuery(id)) } returns
                Err(BreedingResultNotFound(id))

            tester
                .get()
                .uri(uri, worldId, id)
                .assertThat()
                .hasStatus(HttpStatus.NOT_FOUND)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.error_code")
                .isEqualTo("breeding-result-not-found")
        }
    }
}
