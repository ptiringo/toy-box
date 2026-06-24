package com.example.api.controller.breeding

import com.example.api.application.studbook.breeding.RegisterBreedingRegistrationCommand
import com.example.api.application.studbook.breeding.RegisterBreedingRegistrationUseCase
import com.example.api.application.studbook.breeding.RegisterBreedingRegistrationUseCaseError
import com.example.api.config.ClockConfiguration
import com.example.api.domain.shared.Command
import com.example.api.domain.studbook.model.breeding.BreedingFixture
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import java.util.UUID
import org.junit.jupiter.api.Test
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.TestConstructor
import org.springframework.test.context.TestConstructor.AutowireMode
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.assertj.MockMvcTester

@WebMvcTest(BreedingRegistrationController::class)
@Import(ClockConfiguration::class)
@TestConstructor(autowireMode = AutowireMode.ALL)
class BreedingRegistrationControllerTest(val mockMvc: MockMvc) {
    @MockkBean
    private lateinit var registerBreedingRegistration: RegisterBreedingRegistrationUseCase

    private val tester = MockMvcTester.create(mockMvc)

    private val uri = "/api/breedingRegistrations"

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
    fun `正常な入力で 201 Created と登録された繁殖登録が返ること`() {
        val saved = BreedingFixture.breedingRegistration()
        every {
            registerBreedingRegistration(any<Command<RegisterBreedingRegistrationCommand>>())
        } returns Ok(saved)

        tester
            .post()
            .uri(uri)
            .contentType(MediaType.APPLICATION_JSON)
            .content(validBody)
            .assertThat()
            .hasStatus(HttpStatus.CREATED)
            .bodyJson()
            .extractingPath("$.role")
            .isEqualTo("BROODMARE")
    }

    @Test
    fun `登録番号がブランクのとき 400 と problem+json が返ること`() {
        every {
            registerBreedingRegistration(any<Command<RegisterBreedingRegistrationCommand>>())
        } returns Err(RegisterBreedingRegistrationUseCaseError.BlankRegistrationNumber)

        tester
            .post()
            .uri(uri)
            .contentType(MediaType.APPLICATION_JSON)
            .content(validBody)
            .assertThat()
            .hasStatus(HttpStatus.BAD_REQUEST)
            .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
    }

    @Test
    fun `対象の軽種馬が存在しないとき 422 と problem+json が返ること`() {
        val bloodHorseId = UUID.fromString("11111111-1111-1111-1111-111111111111")
        every {
            registerBreedingRegistration(any<Command<RegisterBreedingRegistrationCommand>>())
        } returns Err(RegisterBreedingRegistrationUseCaseError.BloodHorseNotFound(bloodHorseId))

        tester
            .post()
            .uri(uri)
            .contentType(MediaType.APPLICATION_JSON)
            .content(validBody)
            .assertThat()
            .hasStatus(HttpStatus.UNPROCESSABLE_ENTITY)
            .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
            .bodyJson()
            .extractingPath("$.blood_horse_id")
            .isEqualTo("11111111-1111-1111-1111-111111111111")
    }
}
