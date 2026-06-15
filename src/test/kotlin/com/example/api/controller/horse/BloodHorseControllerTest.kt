package com.example.api.controller.horse

import com.example.api.application.horseracing.horse.RegisterInStudBookCommand
import com.example.api.application.horseracing.horse.RegisterInStudBookUseCase
import com.example.api.application.horseracing.horse.RegisterInStudBookUseCaseError
import com.example.api.domain.horseracing.model.horse.bloodhorse.BloodHorseFixture
import com.example.api.domain.horseracing.model.horse.bloodhorse.Sex
import com.example.api.domain.horseracing.service.horse.RegisterInStudBookError
import com.example.api.domain.shared.Command
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import java.util.UUID
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.TestConstructor
import org.springframework.test.context.TestConstructor.AutowireMode
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.assertj.MockMvcTester

@WebMvcTest(BloodHorseController::class)
@TestConstructor(autowireMode = AutowireMode.ALL)
class BloodHorseControllerTest(val mockMvc: MockMvc) {
    @MockkBean private lateinit var registerInStudBook: RegisterInStudBookUseCase

    private val tester = MockMvcTester.create(mockMvc)

    /** デシリアライズに通る正しいリクエストボディ。ユースケースはモックのため中身の整合は問われない。 */
    private val validBody =
        """
        {
            "sireId": "11111111-1111-1111-1111-111111111111",
            "damId": "22222222-2222-2222-2222-222222222222",
            "sex": "MALE",
            "coatColor": "BAY",
            "breedType": "THOROUGHBRED",
            "dateOfBirth": "2023-03-15",
            "breeder": "ノーザンファーム",
            "microchipNumber": "392140000000001",
            "dnaParentage": "CONSISTENT",
            "registrationNumber": "2023104567"
        }
        """
            .trimIndent()

    @Nested
    inner class SuccessCase {
        @Test
        fun `正常な入力で 201 Created と登録結果が返ること`() {
            val saved = BloodHorseFixture.bloodHorse(sex = Sex.MALE)
            every { registerInStudBook(any<Command<RegisterInStudBookCommand>>()) } returns
                Ok(saved)

            tester
                .post()
                .uri("/api/blood_horses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody)
                .assertThat()
                .hasStatus(HttpStatus.CREATED)
                .bodyJson()
                .extractingPath("$.registrationNumber")
                .isEqualTo("2023104567")
        }
    }

    @Nested
    inner class FailureCase {
        @Test
        fun `InvalidMicrochipNumber で 400 と problem+json が返ること`() {
            every { registerInStudBook(any<Command<RegisterInStudBookCommand>>()) } returns
                Err(RegisterInStudBookUseCaseError.InvalidMicrochipNumber)

            tester
                .post()
                .uri("/api/blood_horses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody)
                .assertThat()
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.errorCode")
                .isEqualTo("invalid-microchip-number")
        }

        @Test
        fun `SireNotFound で 422 と sireId 付きの problem+json が返ること`() {
            val sireId = UUID.fromString("11111111-1111-1111-1111-111111111111")
            every { registerInStudBook(any<Command<RegisterInStudBookCommand>>()) } returns
                Err(RegisterInStudBookUseCaseError.SireNotFound(sireId))

            tester
                .post()
                .uri("/api/blood_horses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody)
                .assertThat()
                .hasStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.sireId")
                .isEqualTo(sireId.toString())
        }

        @Test
        fun `前提条件違反（SireNotMale）が 422 と problem+json に変換されること`() {
            every { registerInStudBook(any<Command<RegisterInStudBookCommand>>()) } returns
                Err(
                    RegisterInStudBookUseCaseError.PreconditionViolated(
                        RegisterInStudBookError.SireNotMale
                    )
                )

            tester
                .post()
                .uri("/api/blood_horses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody)
                .assertThat()
                .hasStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.errorCode")
                .isEqualTo("sire-not-male")
        }
    }
}
