package com.example.api.controller.breeding

import com.example.api.application.horseracing.breeding.RecordCoveringCommand
import com.example.api.application.horseracing.breeding.RecordCoveringUseCase
import com.example.api.application.horseracing.breeding.RecordCoveringUseCaseError
import com.example.api.application.horseracing.breeding.RecordUncoveredCommand
import com.example.api.application.horseracing.breeding.RecordUncoveredUseCase
import com.example.api.application.horseracing.breeding.RecordUncoveredUseCaseError
import com.example.api.application.horseracing.breeding.ReportFoalingCommand
import com.example.api.application.horseracing.breeding.ReportFoalingUseCase
import com.example.api.application.horseracing.breeding.ReportFoalingUseCaseError
import com.example.api.config.ClockConfiguration
import com.example.api.domain.horseracing.model.breeding.BreedingFixture
import com.example.api.domain.horseracing.model.breeding.BreedingRegion
import com.example.api.domain.horseracing.model.breeding.CoveringValidityError
import com.example.api.domain.horseracing.model.breeding.FoalingOutcome
import com.example.api.domain.horseracing.model.breeding.RecordCoveringError
import com.example.api.domain.horseracing.model.breeding.RecordUncoveredError
import com.example.api.domain.horseracing.model.breeding.ValidityPeriod
import com.example.api.domain.shared.Command
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import java.time.LocalDate
import java.time.Year
import java.util.UUID
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.TestConstructor
import org.springframework.test.context.TestConstructor.AutowireMode
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.assertj.MockMvcTester

@WebMvcTest(BreedingResultController::class)
@Import(ClockConfiguration::class)
@TestConstructor(autowireMode = AutowireMode.ALL)
class BreedingResultControllerTest(val mockMvc: MockMvc) {
    @MockkBean private lateinit var recordCovering: RecordCoveringUseCase
    @MockkBean private lateinit var recordUncovered: RecordUncoveredUseCase
    @MockkBean private lateinit var reportFoaling: ReportFoalingUseCase

    private val tester = MockMvcTester.create(mockMvc)

    @Nested
    inner class RecordCoveringCase {
        private val uri = "/api/breedingResults"

        /** デシリアライズに通る正しい種付記録リクエストボディ。ユースケースはモックのため中身の整合は問われない。 */
        private val validBody =
            """
            {
                "breeding_registration_id": "11111111-1111-1111-1111-111111111111",
                "covering": {
                    "stallion_registration_id": "22222222-2222-2222-2222-222222222222",
                    "covering_date": "2024-04-01",
                    "certificate_number": "C-2024-0001"
                }
            }
            """
                .trimIndent()

        @Test
        fun `正常な入力で 201 Created と起票された繁殖成績が返ること`() {
            val saved = BreedingFixture.breedingResult()
            every { recordCovering(any<Command<RecordCoveringCommand>>()) } returns Ok(saved)

            tester
                .post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody)
                .assertThat()
                .hasStatus(HttpStatus.CREATED)
                .bodyJson()
                .extractingPath("$.breeding_year")
                .isEqualTo(2024)
        }

        @Test
        fun `InvalidCertificateNumber で 400 と problem+json が返ること`() {
            every { recordCovering(any<Command<RecordCoveringCommand>>()) } returns
                Err(RecordCoveringUseCaseError.InvalidCertificateNumber)

            tester
                .post()
                .uri(uri)
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
        fun `BreedingRegistrationNotFound で 422 と breedingRegistrationId 付きの problem+json が返ること`() {
            val id = UUID.fromString("11111111-1111-1111-1111-111111111111")
            every { recordCovering(any<Command<RecordCoveringCommand>>()) } returns
                Err(RecordCoveringUseCaseError.BreedingRegistrationNotFound(id))

            tester
                .post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody)
                .assertThat()
                .hasStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.breeding_registration_id")
                .isEqualTo(id.toString())
        }

        @Test
        fun `StallionRegistrationNotFound で 422 と stallionRegistrationId 付きの problem+json が返ること`() {
            val id = UUID.fromString("22222222-2222-2222-2222-222222222222")
            every { recordCovering(any<Command<RecordCoveringCommand>>()) } returns
                Err(RecordCoveringUseCaseError.StallionRegistrationNotFound(id))

            tester
                .post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody)
                .assertThat()
                .hasStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.stallion_registration_id")
                .isEqualTo(id.toString())
        }

        @Test
        fun `前提条件違反（NotStallion）が 422 と problem+json に変換されること`() {
            every { recordCovering(any<Command<RecordCoveringCommand>>()) } returns
                Err(
                    RecordCoveringUseCaseError.PreconditionViolated(RecordCoveringError.NotStallion)
                )

            tester
                .post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody)
                .assertThat()
                .hasStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.error_code")
                .isEqualTo("not-stallion")
        }

        @Test
        fun `前提条件違反（NotBroodmare）が 422 と problem+json に変換されること`() {
            every { recordCovering(any<Command<RecordCoveringCommand>>()) } returns
                Err(
                    RecordCoveringUseCaseError.PreconditionViolated(
                        RecordCoveringError.NotBroodmare
                    )
                )

            tester
                .post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody)
                .assertThat()
                .hasStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.error_code")
                .isEqualTo("not-broodmare")
        }

        @Test
        fun `有効期間外（InvalidCovering OutsideValidPeriod）が 422 と problem+json に変換されること`() {
            val period =
                ValidityPeriod.create(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 3, 31)).unwrap()
            every { recordCovering(any<Command<RecordCoveringCommand>>()) } returns
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
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody)
                .assertThat()
                .hasStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.error_code")
                .isEqualTo("covering-outside-valid-period")
        }

        @Test
        fun `有効区域外（InvalidCovering OutsideValidRegion）が 422 と problem+json に変換されること`() {
            val hokkaido = BreedingRegion.create("北海道").unwrap()
            val aomori = BreedingRegion.create("青森").unwrap()
            every { recordCovering(any<Command<RecordCoveringCommand>>()) } returns
                Err(
                    RecordCoveringUseCaseError.PreconditionViolated(
                        RecordCoveringError.InvalidCovering(
                            CoveringValidityError.OutsideValidRegion(aomori, setOf(hokkaido))
                        )
                    )
                )

            tester
                .post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody)
                .assertThat()
                .hasStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.error_code")
                .isEqualTo("covering-outside-valid-region")
        }

        @Test
        fun `重複記録（AlreadyRecordedForYear）が 409 と繁殖年つきの problem+json に変換されること`() {
            val existing = BreedingFixture.breedingResult()
            every { recordCovering(any<Command<RecordCoveringCommand>>()) } returns
                Err(
                    RecordCoveringUseCaseError.PreconditionViolated(
                        RecordCoveringError.AlreadyRecordedForYear(Year.of(2024), existing.id)
                    )
                )

            tester
                .post()
                .uri(uri)
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
        private val uri = "/api/breedingResults"

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
            every { recordUncovered(any<Command<RecordUncoveredCommand>>()) } returns Ok(saved)

            tester
                .post()
                .uri(uri)
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
                .uri(uri)
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
            every { recordUncovered(any<Command<RecordUncoveredCommand>>()) } returns
                Err(RecordUncoveredUseCaseError.BreedingRegistrationNotFound(id))

            tester
                .post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody)
                .assertThat()
                .hasStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.breeding_registration_id")
                .isEqualTo(id.toString())
        }

        @Test
        fun `前提条件違反（NotBroodmare）が 422 と problem+json に変換されること`() {
            every { recordUncovered(any<Command<RecordUncoveredCommand>>()) } returns
                Err(
                    RecordUncoveredUseCaseError.PreconditionViolated(
                        RecordUncoveredError.NotBroodmare
                    )
                )

            tester
                .post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody)
                .assertThat()
                .hasStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.error_code")
                .isEqualTo("not-broodmare")
        }

        @Test
        fun `重複記録（AlreadyRecordedForYear）が 409 と繁殖年つきの problem+json に変換されること`() {
            val existing = BreedingFixture.uncoveredBreedingResult()
            every { recordUncovered(any<Command<RecordUncoveredCommand>>()) } returns
                Err(
                    RecordUncoveredUseCaseError.PreconditionViolated(
                        RecordUncoveredError.AlreadyRecordedForYear(Year.of(2024), existing.id)
                    )
                )

            tester
                .post()
                .uri(uri)
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
        private val uri = "/api/breedingResults/$breedingResultId:reportFoaling"
        private val liveFoalBody = """{ "outcome": "LIVE_FOAL", "foaling_date": "2025-03-20" }"""

        @Test
        fun `正常な入力で 200 OK と更新後の繁殖成績が返ること`() {
            val reported =
                BreedingFixture.breedingResult()
                    .recordFoaling(FoalingOutcome.LiveFoal(LocalDate.of(2025, 3, 20)))
                    .unwrap()
            every { reportFoaling(any<Command<ReportFoalingCommand>>()) } returns Ok(reported)

            tester
                .post()
                .uri(uri)
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
                .uri(uri)
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
            every { reportFoaling(any<Command<ReportFoalingCommand>>()) } returns
                Err(ReportFoalingUseCaseError.BreedingResultNotFound(id))

            tester
                .post()
                .uri(uri)
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
            every { reportFoaling(any<Command<ReportFoalingCommand>>()) } returns
                Err(
                    ReportFoalingUseCaseError.AlreadyReported(
                        FoalingOutcome.LiveFoal(LocalDate.of(2025, 3, 20))
                    )
                )

            tester
                .post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{ "outcome": "NOT_CONCEIVED" }""")
                .assertThat()
                .hasStatus(HttpStatus.CONFLICT)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.error_code")
                .isEqualTo("foaling-already-recorded")
        }
    }
}
