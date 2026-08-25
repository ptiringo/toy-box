package com.example.api.controller.breeding

import com.example.api.application.iam.world.WorldQueries
import com.example.api.application.studbook.breeding.CoveringReportDetailView
import com.example.api.application.studbook.breeding.CoveringReportNotFound
import com.example.api.application.studbook.breeding.GetCoveringReportQuery
import com.example.api.application.studbook.breeding.GetCoveringReportUseCase
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
@WebMvcTest(CoveringReportController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ClockConfiguration::class)
@TestConstructor(autowireMode = AutowireMode.ALL)
class CoveringReportControllerTest(val mockMvc: MockMvc) {
    @MockkBean private lateinit var submitCoveringReport: SubmitCoveringReportUseCase
    @MockkBean private lateinit var getCoveringReport: GetCoveringReportUseCase

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

    @Test
    fun `存在する ID の照会で 200 と種付成績報告リソースが返ること`() {
        val id = UUID.fromString("55555555-5555-5555-5555-555555555555")
        every { getCoveringReport(any<Actor>(), GetCoveringReportQuery(id)) } returns
            Ok(
                CoveringReportDetailView(
                    id = id,
                    stallionBreedingRegistrationId = generateId(),
                    coveringYear = 2024,
                    // 種付年 2024 の期限は当年 9/30。その翌日の提出は期限超過として応答に表れる。
                    submittedOn = LocalDate.of(2024, 10, 1),
                )
            )

        tester
            .get()
            .uri("$uri/{id}", worldId, id)
            .assertThat()
            .hasStatusOk()
            .bodyJson()
            .extractingPath("$.submitted_late")
            .isEqualTo(true)
    }

    @Test
    fun `照会対象が不在なら 404 と problem+json が返ること`() {
        val id = UUID.fromString("55555555-5555-5555-5555-555555555555")
        every { getCoveringReport(any<Actor>(), GetCoveringReportQuery(id)) } returns
            Err(CoveringReportNotFound(id))

        tester
            .get()
            .uri("$uri/{id}", worldId, id)
            .assertThat()
            .hasStatus(HttpStatus.NOT_FOUND)
            .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
            .bodyJson()
            .extractingPath("$.error_code")
            .isEqualTo("covering-report-not-found")
    }
}
