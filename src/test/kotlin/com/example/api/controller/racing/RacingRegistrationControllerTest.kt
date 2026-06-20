package com.example.api.controller.racing

import com.example.api.application.horseracing.racing.RegisterAsRacehorseCommand
import com.example.api.application.horseracing.racing.RegisterAsRacehorseUseCase
import com.example.api.application.horseracing.racing.RegisterAsRacehorseUseCaseError
import com.example.api.config.ClockConfiguration
import com.example.api.domain.horseracing.model.horse.bloodhorse.BloodHorseId
import com.example.api.domain.horseracing.model.racing.RacingRegistrationFixture
import com.example.api.domain.horseracing.service.racing.RegisterAsRacehorseError
import com.example.api.domain.shared.Command
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
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

@WebMvcTest(RacingRegistrationController::class)
@Import(ClockConfiguration::class)
@TestConstructor(autowireMode = AutowireMode.ALL)
class RacingRegistrationControllerTest(val mockMvc: MockMvc) {
    @MockkBean private lateinit var registerAsRacehorse: RegisterAsRacehorseUseCase

    private val tester = MockMvcTester.create(mockMvc)

    private val bloodHorseId = "11111111-1111-1111-1111-111111111111"

    /** デシリアライズに通る正しいリクエストボディ。ユースケースはモックのため中身の整合は問われない。 */
    private val validBody =
        """
        {
            "bloodHorseId": "$bloodHorseId",
            "registrationNumber": "R2024001"
        }
        """
            .trimIndent()

    @Nested
    inner class SuccessCase {
        @Test
        fun `正常な入力で 201 Created と登録結果が返ること`() {
            val saved =
                RacingRegistrationFixture.racingRegistration(
                    racehorseId = BloodHorseId(UUID.fromString(bloodHorseId))
                )
            every { registerAsRacehorse(any<Command<RegisterAsRacehorseCommand>>()) } returns
                Ok(saved)

            tester
                .post()
                .uri("/api/racing_registrations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody)
                .assertThat()
                .hasStatus(HttpStatus.CREATED)
                .bodyJson()
                .extractingPath("$.registrationNumber")
                .isEqualTo("R2024001")
        }
    }

    @Nested
    inner class FailureCase {
        @Test
        fun `InvalidRegistrationNumber で 400 と problem+json が返ること`() {
            every { registerAsRacehorse(any<Command<RegisterAsRacehorseCommand>>()) } returns
                Err(RegisterAsRacehorseUseCaseError.InvalidRegistrationNumber)

            tester
                .post()
                .uri("/api/racing_registrations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody)
                .assertThat()
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.errorCode")
                .isEqualTo("invalid-registration-number")
        }

        @Test
        fun `HorseNotFound で 422 と bloodHorseId 付きの problem+json が返ること`() {
            val id = UUID.fromString(bloodHorseId)
            every { registerAsRacehorse(any<Command<RegisterAsRacehorseCommand>>()) } returns
                Err(RegisterAsRacehorseUseCaseError.HorseNotFound(id))

            tester
                .post()
                .uri("/api/racing_registrations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody)
                .assertThat()
                .hasStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.bloodHorseId")
                .isEqualTo(id.toString())
        }

        @Test
        fun `前提条件違反（NotNamed）が 422 と problem+json に変換されること`() {
            every { registerAsRacehorse(any<Command<RegisterAsRacehorseCommand>>()) } returns
                Err(
                    RegisterAsRacehorseUseCaseError.PreconditionViolated(
                        RegisterAsRacehorseError.NotNamed
                    )
                )

            tester
                .post()
                .uri("/api/racing_registrations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody)
                .assertThat()
                .hasStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.errorCode")
                .isEqualTo("horse-not-named")
        }
    }
}
