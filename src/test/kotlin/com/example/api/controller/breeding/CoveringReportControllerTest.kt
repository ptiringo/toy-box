package com.example.api.controller.breeding

import com.example.api.application.iam.world.WorldQueries
import com.example.api.application.studbook.breeding.SubmitCoveringReportCommand
import com.example.api.application.studbook.breeding.SubmitCoveringReportUseCase
import com.example.api.application.studbook.breeding.SubmitCoveringReportUseCaseError
import com.example.api.config.ClockConfiguration
import com.example.api.controller.ActorArgumentResolver
import com.example.api.domain.iam.model.account.AccountRepository
import com.example.api.domain.shared.AccountId
import com.example.api.domain.shared.Actor
import com.example.api.domain.shared.Command
import com.example.api.domain.shared.WorldId
import com.example.api.domain.shared.generateId
import com.example.api.domain.studbook.model.breeding.BreedingFixture
import com.example.api.domain.studbook.model.breeding.SubmitCoveringReportError
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import java.time.LocalDate
import java.time.Year
import java.util.UUID
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.TestConstructor
import org.springframework.test.context.TestConstructor.AutowireMode
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.assertj.MockMvcTester

@WebMvcTest(CoveringReportController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ClockConfiguration::class)
@TestConstructor(autowireMode = AutowireMode.ALL)
class CoveringReportControllerTest(val mockMvc: MockMvc) {
    @MockkBean private lateinit var submitCoveringReport: SubmitCoveringReportUseCase

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

    private val uri = "/api/worlds/{worldId}/coveringReports"

    /** デシリアライズに通る正しい提出リクエストボディ。ユースケースはモックのため中身の整合は問われない。 */
    private val validBody =
        """
        {
            "stallion_breeding_registration_id": "11111111-1111-1111-1111-111111111111",
            "covering_year": 2024
        }
        """
            .trimIndent()

    @Test
    fun `正常な入力で 201 Created と提出された種付成績報告が返ること`() {
        val saved =
            BreedingFixture.coveringReport(
                coveringYear = Year.of(2024),
                submittedOn = LocalDate.of(2024, 10, 1),
            )
        every {
            submitCoveringReport(any<Actor>(), any<Command<SubmitCoveringReportCommand>>())
        } returns Ok(saved)

        tester
            .post()
            .uri(uri, worldId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(validBody)
            .assertThat()
            .hasStatus(HttpStatus.CREATED)
            .bodyJson()
            .extractingPath("$.submitted_late")
            .isEqualTo(true)
    }

    @Test
    fun `StallionRegistrationNotFound で 422 と problem+json が返ること`() {
        every {
            submitCoveringReport(any<Actor>(), any<Command<SubmitCoveringReportCommand>>())
        } returns
            Err(SubmitCoveringReportUseCaseError.StallionRegistrationNotFound(UUID.randomUUID()))

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
            .isEqualTo("stallion-registration-not-found")
    }

    @Test
    fun `NotStallion で 422 と problem+json が返ること`() {
        every {
            submitCoveringReport(any<Actor>(), any<Command<SubmitCoveringReportCommand>>())
        } returns
            Err(
                SubmitCoveringReportUseCaseError.PreconditionViolated(
                    SubmitCoveringReportError.NotStallion
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
            .isEqualTo("not-stallion")
    }

    @Test
    fun `AlreadySubmittedForYear で 409 と problem+json が返ること`() {
        val existing = BreedingFixture.coveringReport()
        every {
            submitCoveringReport(any<Actor>(), any<Command<SubmitCoveringReportCommand>>())
        } returns
            Err(
                SubmitCoveringReportUseCaseError.PreconditionViolated(
                    SubmitCoveringReportError.AlreadySubmittedForYear(Year.of(2024), existing.id)
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
            .extractingPath("$.error_code")
            .isEqualTo("covering-report-already-submitted-for-year")
    }
}
