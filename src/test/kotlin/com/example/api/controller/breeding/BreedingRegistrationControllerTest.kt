package com.example.api.controller.breeding

import com.example.api.application.iam.world.WorldQueries
import com.example.api.application.studbook.breeding.BreedingRegistrationDetailView
import com.example.api.application.studbook.breeding.BreedingRegistrationNotFound
import com.example.api.application.studbook.breeding.GetBreedingRegistrationQuery
import com.example.api.application.studbook.breeding.GetBreedingRegistrationUseCase
import com.example.api.application.studbook.breeding.RegisterBreedingRegistrationCommand
import com.example.api.application.studbook.breeding.RegisterBreedingRegistrationUseCase
import com.example.api.application.studbook.breeding.RegisterBreedingRegistrationUseCaseError
import com.example.api.config.ClockConfiguration
import com.example.api.controller.ActorArgumentResolver
import com.example.api.domain.iam.model.account.AccountRepository
import com.example.api.domain.shared.AccountId
import com.example.api.domain.shared.Actor
import com.example.api.domain.shared.Command
import com.example.api.domain.shared.WorldId
import com.example.api.domain.shared.generateId
import com.example.api.domain.studbook.model.breeding.BreedingFixture
import com.example.api.domain.studbook.model.breeding.BreedingRetirement
import com.example.api.domain.studbook.model.breeding.BreedingRole
import com.example.api.domain.studbook.model.breeding.RetirementReason
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import java.time.LocalDate
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

@WebMvcTest(BreedingRegistrationController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ClockConfiguration::class)
@TestConstructor(autowireMode = AutowireMode.ALL)
class BreedingRegistrationControllerTest(val mockMvc: MockMvc) {
    @MockkBean
    private lateinit var registerBreedingRegistration: RegisterBreedingRegistrationUseCase

    @MockkBean private lateinit var getBreedingRegistration: GetBreedingRegistrationUseCase

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

    private val uri = "/api/worlds/{worldId}/breedingRegistrations"

    /** デシリアライズに通る正しい繁殖登録リクエストボディ。ユースケースはモックのため中身の整合は問われない。 */
    private val validBody =
        """
        {
            "blood_horse_id": "11111111-1111-1111-1111-111111111111",
            "registration_number": "B-2024-0001"
        }
        """
            .trimIndent()

    @Test
    fun `正常な入力で 201 Created と成立した繁殖登録が返ること`() {
        val saved = BreedingFixture.breedingRegistration()
        every {
            registerBreedingRegistration(
                any<Actor>(),
                any<Command<RegisterBreedingRegistrationCommand>>(),
            )
        } returns Ok(saved)

        tester
            .post()
            .uri(uri, worldId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(validBody)
            .assertThat()
            .hasStatus(HttpStatus.CREATED)
            .bodyJson()
            .extractingPath("$.role")
            .isEqualTo("BROODMARE")
    }

    @Test
    fun `InvalidRegistrationNumber で 400 と problem+json が返ること`() {
        every {
            registerBreedingRegistration(
                any<Actor>(),
                any<Command<RegisterBreedingRegistrationCommand>>(),
            )
        } returns Err(RegisterBreedingRegistrationUseCaseError.InvalidRegistrationNumber)

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
            .isEqualTo("invalid-breeding-registration-number")
    }

    @Test
    fun `RegistrationNumberAlreadyTaken で 409 と繁殖登録固有の errorCode が返ること`() {
        // 繁殖登録原簿は血統登録原簿と別の採番空間なので、血統側とは別の errorCode を割り当てる。
        every {
            registerBreedingRegistration(
                any<Actor>(),
                any<Command<RegisterBreedingRegistrationCommand>>(),
            )
        } returns
            Err(
                RegisterBreedingRegistrationUseCaseError.RegistrationNumberAlreadyTaken(
                    "B-2024-0001"
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
            .extractingPath("$.registration_number")
            .isEqualTo("B-2024-0001")
    }

    @Test
    fun `存在する ID の照会で 200 と繁殖登録リソースが返ること`() {
        val id = UUID.fromString("22222222-2222-2222-2222-222222222222")
        val horseId = UUID.fromString("33333333-3333-3333-3333-333333333333")
        every { getBreedingRegistration(any<Actor>(), GetBreedingRegistrationQuery(id)) } returns
            Ok(
                BreedingRegistrationDetailView(
                    id = id,
                    registrationNumber = "B-2024-0001",
                    registeredHorseId = horseId,
                    role = BreedingRole.STALLION,
                    retirement =
                        BreedingRetirement(RetirementReason.DEATH, LocalDate.of(2025, 3, 1)),
                )
            )

        tester
            .get()
            .uri("$uri/{id}", worldId, id)
            .assertThat()
            .hasStatusOk()
            .bodyJson()
            .extractingPath("$.retirement.reason")
            .isEqualTo("DEATH")
    }

    @Test
    fun `照会対象が不在なら 404 と problem+json が返ること`() {
        val id = UUID.fromString("22222222-2222-2222-2222-222222222222")
        every { getBreedingRegistration(any<Actor>(), GetBreedingRegistrationQuery(id)) } returns
            Err(BreedingRegistrationNotFound(id))

        tester
            .get()
            .uri("$uri/{id}", worldId, id)
            .assertThat()
            .hasStatus(HttpStatus.NOT_FOUND)
            .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
            .bodyJson()
            .extractingPath("$.error_code")
            .isEqualTo("registration-not-found")
    }

    @Test
    fun `HorseNotFound で 422 と bloodHorseId 付きの problem+json が返ること`() {
        val id = UUID.fromString("11111111-1111-1111-1111-111111111111")
        every {
            registerBreedingRegistration(
                any<Actor>(),
                any<Command<RegisterBreedingRegistrationCommand>>(),
            )
        } returns Err(RegisterBreedingRegistrationUseCaseError.HorseNotFound(id))

        tester
            .post()
            .uri(uri, worldId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(validBody)
            .assertThat()
            .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
            .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
            .bodyJson()
            .extractingPath("$.blood_horse_id")
            .isEqualTo(id.toString())
    }
}
