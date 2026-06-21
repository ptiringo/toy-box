package com.example.api.controller.horse

import com.example.api.application.horseracing.horse.NameHorseCommand
import com.example.api.application.horseracing.horse.NameHorseUseCase
import com.example.api.application.horseracing.horse.NameHorseUseCaseError
import com.example.api.application.horseracing.horse.RegisterInStudBookCommand
import com.example.api.application.horseracing.horse.RegisterInStudBookUseCase
import com.example.api.application.horseracing.horse.RegisterInStudBookUseCaseError
import com.example.api.config.ClockConfiguration
import com.example.api.domain.horseracing.model.horse.bloodhorse.BloodHorseFixture
import com.example.api.domain.horseracing.model.horse.bloodhorse.HorseName
import com.example.api.domain.horseracing.model.horse.bloodhorse.Sex
import com.example.api.domain.horseracing.service.horse.RegisterInStudBookError
import com.example.api.domain.shared.Command
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
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

@WebMvcTest(BloodHorseController::class)
@Import(ClockConfiguration::class)
@TestConstructor(autowireMode = AutowireMode.ALL)
class BloodHorseControllerTest(val mockMvc: MockMvc) {
    @MockkBean private lateinit var registerInStudBook: RegisterInStudBookUseCase
    @MockkBean private lateinit var nameHorse: NameHorseUseCase

    private val tester = MockMvcTester.create(mockMvc)

    /** デシリアライズに通る正しいリクエストボディ。ユースケースはモックのため中身の整合は問われない。 */
    private val validBody =
        """
        {
            "sire_id": "11111111-1111-1111-1111-111111111111",
            "dam_id": "22222222-2222-2222-2222-222222222222",
            "sex": "MALE",
            "coat_color": "BAY",
            "breed_type": "THOROUGHBRED",
            "date_of_birth": "2023-03-15",
            "breeder": "ノーザンファーム",
            "microchip_number": "392140000000001",
            "dna_parentage": "CONSISTENT",
            "registration_number": "2023104567"
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
                .uri("/api/bloodHorses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody)
                .assertThat()
                .hasStatus(HttpStatus.CREATED)
                .bodyJson()
                .extractingPath("$.registration_number")
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
                .uri("/api/bloodHorses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody)
                .assertThat()
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.error_code")
                .isEqualTo("invalid-microchip-number")
        }

        @Test
        fun `SireNotFound で 422 と sireId 付きの problem+json が返ること`() {
            val sireId = UUID.fromString("11111111-1111-1111-1111-111111111111")
            every { registerInStudBook(any<Command<RegisterInStudBookCommand>>()) } returns
                Err(RegisterInStudBookUseCaseError.SireNotFound(sireId))

            tester
                .post()
                .uri("/api/bloodHorses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody)
                .assertThat()
                .hasStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.sire_id")
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
                .uri("/api/bloodHorses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody)
                .assertThat()
                .hasStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.error_code")
                .isEqualTo("sire-not-male")
        }
    }

    @Nested
    inner class RegisterNameCase {
        private val bloodHorseId = "33333333-3333-3333-3333-333333333333"
        private val uri = "/api/bloodHorses/$bloodHorseId:registerName"
        private val body = """{ "name": "オグリキャップ" }"""

        @Test
        fun `正常な入力で 200 OK と命名結果が返ること`() {
            val named =
                BloodHorseFixture.bloodHorse()
                    .assignName(HorseName.create("オグリキャップ").unwrap())
                    .unwrap()
            every { nameHorse(any<Command<NameHorseCommand>>()) } returns Ok(named)

            tester
                .post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .assertThat()
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .extractingPath("$.name")
                .isEqualTo("オグリキャップ")
        }

        @Test
        fun `InvalidName で 400 と problem+json が返ること`() {
            every { nameHorse(any<Command<NameHorseCommand>>()) } returns
                Err(NameHorseUseCaseError.InvalidName)

            tester
                .post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .assertThat()
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.error_code")
                .isEqualTo("invalid-horse-name")
        }

        @Test
        fun `HorseNotFound で 404 と bloodHorseId 付きの problem+json が返ること`() {
            val id = UUID.fromString(bloodHorseId)
            every { nameHorse(any<Command<NameHorseCommand>>()) } returns
                Err(NameHorseUseCaseError.HorseNotFound(id))

            tester
                .post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .assertThat()
                .hasStatus(HttpStatus.NOT_FOUND)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.blood_horse_id")
                .isEqualTo(id.toString())
        }

        @Test
        fun `AlreadyNamed で 409 と problem+json が返ること`() {
            every { nameHorse(any<Command<NameHorseCommand>>()) } returns
                Err(NameHorseUseCaseError.AlreadyNamed("トウカイテイオー"))

            tester
                .post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .assertThat()
                .hasStatus(HttpStatus.CONFLICT)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.error_code")
                .isEqualTo("horse-already-named")
        }
    }
}
