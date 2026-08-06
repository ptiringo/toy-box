package com.example.api.controller.horse

import com.example.api.application.studbook.horse.BloodHorseDetailView
import com.example.api.application.studbook.horse.BloodHorseNotFound
import com.example.api.application.studbook.horse.BloodHorseView
import com.example.api.application.studbook.horse.GetBloodHorseQuery
import com.example.api.application.studbook.horse.GetBloodHorseUseCase
import com.example.api.application.studbook.horse.ListBloodHorsesUseCase
import com.example.api.application.studbook.horse.NameHorseCommand
import com.example.api.application.studbook.horse.NameHorseUseCase
import com.example.api.application.studbook.horse.NameHorseUseCaseError
import com.example.api.application.studbook.horse.RegisterCarriedOverHorseCommand
import com.example.api.application.studbook.horse.RegisterCarriedOverHorseUseCase
import com.example.api.application.studbook.horse.RegisterCarriedOverHorseUseCaseError
import com.example.api.application.studbook.horse.RegisterImportedHorseCommand
import com.example.api.application.studbook.horse.RegisterImportedHorseUseCase
import com.example.api.application.studbook.horse.RegisterImportedHorseUseCaseError
import com.example.api.application.studbook.horse.RegisterInStudBookCommand
import com.example.api.application.studbook.horse.RegisterInStudBookUseCase
import com.example.api.application.studbook.horse.RegisterInStudBookUseCaseError
import com.example.api.application.studbook.horse.RegisteredBloodHorse
import com.example.api.config.ClockConfiguration
import com.example.api.controller.horse.request.RegisterBloodHorseRequest
import com.example.api.controller.horse.request.RegisterCarriedOverHorseRequest
import com.example.api.controller.horse.request.RegisterImportedHorseRequest
import com.example.api.domain.iam.model.account.AccountRepository
import com.example.api.domain.shared.Command
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseFixture
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseId
import com.example.api.domain.studbook.model.horse.bloodhorse.BreedType
import com.example.api.domain.studbook.model.horse.bloodhorse.CoatColor
import com.example.api.domain.studbook.model.horse.bloodhorse.HorseName
import com.example.api.domain.studbook.model.horse.bloodhorse.Origin
import com.example.api.domain.studbook.model.horse.bloodhorse.RegisterInStudBookError
import com.example.api.domain.studbook.model.horse.bloodhorse.Sex
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import java.time.LocalDate
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
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
import tools.jackson.databind.json.JsonMapper

@WebMvcTest(BloodHorseController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ClockConfiguration::class)
@TestConstructor(autowireMode = AutowireMode.ALL)
class BloodHorseControllerTest(val mockMvc: MockMvc, val jsonMapper: JsonMapper) {
    @MockkBean private lateinit var registerInStudBook: RegisterInStudBookUseCase
    @MockkBean private lateinit var registerImportedHorse: RegisterImportedHorseUseCase
    @MockkBean private lateinit var nameHorse: NameHorseUseCase
    @MockkBean private lateinit var registerCarriedOverHorse: RegisterCarriedOverHorseUseCase
    @MockkBean private lateinit var listBloodHorses: ListBloodHorsesUseCase
    @MockkBean private lateinit var getBloodHorse: GetBloodHorseUseCase

    // WebMvcConfig（CurrentAccountArgumentResolver）が全 @WebMvcTest スライスへ自動で載るため必要（本テストの検証対象ではない）。
    @MockkBean private lateinit var accounts: AccountRepository

    private val tester = MockMvcTester.create(mockMvc)

    /**
     * デシリアライズに通る正しいリクエストボディ。ユースケースはモックのため中身の整合は問われない。
     *
     * 手書き JSON リテラルではなく [RegisterBloodHorseRequest] を実アプリと同じ [jsonMapper] でシリアライズして組み立てる。 DTO
     * のフィールドが変わればボディも追従し、契約とテストの二重管理を避ける。`sireId` は SireNotFound ケースの アサーションと揃えてある。
     */
    private val validBody =
        jsonMapper.writeValueAsString(
            RegisterBloodHorseRequest(
                sireId = UUID.fromString("11111111-1111-1111-1111-111111111111"),
                damId = UUID.fromString("22222222-2222-2222-2222-222222222222"),
                sex = SexDto.MALE,
                coatColor = CoatColorDto.BAY,
                breedType = BreedTypeDto.THOROUGHBRED,
                dateOfBirth = LocalDate.of(2023, 3, 15),
                breeder = "ノーザンファーム",
                microchipNumber = "392140000000001",
                dnaParentage = DnaParentageResultDto.CONSISTENT,
                registrationNumber = "2023104567",
            )
        )

    @Nested
    inner class ListCase {
        @Test
        fun `軽種馬一覧が200で snake_case のサマリ配列として返ること`() {
            val id = UUID.fromString("22222222-2222-2222-2222-222222222222")
            every { listBloodHorses() } returns
                listOf(
                    BloodHorseView(
                        id = id,
                        registrationNumber = "REG-001",
                        sex = Sex.FEMALE,
                        coatColor = CoatColor.BAY,
                        breedType = BreedType.THOROUGHBRED,
                        dateOfBirth = LocalDate.of(2020, 4, 10),
                        breeder = "Coolmore",
                        name = null,
                    )
                )

            val body = tester.get().uri("/api/bloodHorses").assertThat().hasStatusOk().bodyJson()

            body.extractingPath("$[0].id").isEqualTo(id.toString())
            body.extractingPath("$[0].registration_number").isEqualTo("REG-001")
            body.extractingPath("$[0].sex").isEqualTo("FEMALE")
            body.extractingPath("$[0].coat_color").isEqualTo("BAY")
            body.extractingPath("$[0].breed_type").isEqualTo("THOROUGHBRED")
            body.extractingPath("$[0].date_of_birth").isEqualTo("2020-04-10")
            // 未命名は name = null で公開されること
            body.extractingPath("$[0].name").isNull()
        }

        @Test
        fun `登録が無ければ200で空配列を返すこと`() {
            every { listBloodHorses() } returns emptyList()

            tester
                .get()
                .uri("/api/bloodHorses")
                .assertThat()
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$")
                .asArray()
                .isEmpty()
        }
    }

    @Nested
    inner class SuccessCase {
        @Test
        fun `正常な入力で 201 Created と内国産の出自を持つ登録結果が返ること`() {
            val saved =
                RegisteredBloodHorse(
                    BloodHorseFixture.domesticBloodHorse(),
                    BloodHorseFixture.inspection(),
                )
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
                .extractingPath("$.origin.type")
                .isEqualTo("DOMESTIC")
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
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
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
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.error_code")
                .isEqualTo("sire-not-male")
        }

        @Test
        fun `DamNotFound で 422 と damId 付きの problem+json が返ること`() {
            val damId = UUID.fromString("22222222-2222-2222-2222-222222222222")
            every { registerInStudBook(any<Command<RegisterInStudBookCommand>>()) } returns
                Err(RegisterInStudBookUseCaseError.DamNotFound(damId))

            tester
                .post()
                .uri("/api/bloodHorses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody)
                .assertThat()
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.dam_id")
                .isEqualTo(damId.toString())
        }

        @Test
        fun `前提条件違反（DamNotFemale）が 422 と problem+json に変換されること`() {
            every { registerInStudBook(any<Command<RegisterInStudBookCommand>>()) } returns
                Err(
                    RegisterInStudBookUseCaseError.PreconditionViolated(
                        RegisterInStudBookError.DamNotFemale
                    )
                )

            tester
                .post()
                .uri("/api/bloodHorses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody)
                .assertThat()
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.error_code")
                .isEqualTo("dam-not-female")
        }

        @Test
        fun `前提条件違反（ParentageNotConfirmed）が 422 と problem+json に変換されること`() {
            every { registerInStudBook(any<Command<RegisterInStudBookCommand>>()) } returns
                Err(
                    RegisterInStudBookUseCaseError.PreconditionViolated(
                        RegisterInStudBookError.ParentageNotConfirmed
                    )
                )

            tester
                .post()
                .uri("/api/bloodHorses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody)
                .assertThat()
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.error_code")
                .isEqualTo("parentage-not-confirmed")
        }

        @Test
        fun `前提条件違反（BreedMismatch）が 422 と problem+json に変換されること`() {
            every { registerInStudBook(any<Command<RegisterInStudBookCommand>>()) } returns
                Err(
                    RegisterInStudBookUseCaseError.PreconditionViolated(
                        RegisterInStudBookError.BreedMismatch
                    )
                )

            tester
                .post()
                .uri("/api/bloodHorses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody)
                .assertThat()
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.error_code")
                .isEqualTo("breed-mismatch")
        }

        @Test
        fun `前提条件違反（GrayFoalFromNonGrayParents）が 422 と problem+json に変換されること`() {
            every { registerInStudBook(any<Command<RegisterInStudBookCommand>>()) } returns
                Err(
                    RegisterInStudBookUseCaseError.PreconditionViolated(
                        RegisterInStudBookError.GrayFoalFromNonGrayParents
                    )
                )

            tester
                .post()
                .uri("/api/bloodHorses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody)
                .assertThat()
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.error_code")
                .isEqualTo("gray-foal-from-non-gray-parents")
        }

        @Test
        fun `前提条件違反（NonChestnutFoalFromChestnutParents）が 422 と problem+json に変換されること`() {
            every { registerInStudBook(any<Command<RegisterInStudBookCommand>>()) } returns
                Err(
                    RegisterInStudBookUseCaseError.PreconditionViolated(
                        RegisterInStudBookError.NonChestnutFoalFromChestnutParents
                    )
                )

            tester
                .post()
                .uri("/api/bloodHorses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody)
                .assertThat()
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.error_code")
                .isEqualTo("non-chestnut-foal-from-chestnut-parents")
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
                    .aggregate
            every { nameHorse(any<Command<NameHorseCommand>>()) } returns
                Ok(RegisteredBloodHorse(named, BloodHorseFixture.inspection()))

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

        @Test
        fun `NameAlreadyTaken で 409 と problem+json が返ること`() {
            every { nameHorse(any<Command<NameHorseCommand>>()) } returns
                Err(NameHorseUseCaseError.NameAlreadyTaken("オグリキャップ"))

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
                .isEqualTo("horse-name-already-taken")
        }

        @Test
        fun `ConcurrentModification で 409 と problem+json が返ること`() {
            val id = UUID.fromString(bloodHorseId)
            every { nameHorse(any<Command<NameHorseCommand>>()) } returns
                Err(NameHorseUseCaseError.ConcurrentModification(id))

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
                .isEqualTo("concurrent-modification")
        }

        @Test
        fun `InspectionNotFound で 422 と inspection_id 付きの problem+json が返ること`() {
            val inspectionId = UUID.fromString("55555555-5555-5555-5555-555555555555")
            every { nameHorse(any<Command<NameHorseCommand>>()) } returns
                Err(NameHorseUseCaseError.InspectionNotFound(inspectionId))

            val result =
                tester
                    .post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
                    .exchange()

            assertThat(result)
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.error_code")
                .isEqualTo("inspection-not-found")
            assertThat(result)
                .bodyJson()
                .extractingPath("$.inspection_id")
                .isEqualTo(inspectionId.toString())
        }
    }

    @Nested
    inner class RegisterImportedCase {
        private val uri = "/api/bloodHorses:registerImported"

        /** 父母 ID・DNA を持たず、原産国・揚陸日を持つ輸入馬のリクエストボディ。実アプリと同じ [jsonMapper] で DTO をシリアライズして組み立てる。 */
        private val validBody =
            jsonMapper.writeValueAsString(
                RegisterImportedHorseRequest(
                    sex = SexDto.MALE,
                    coatColor = CoatColorDto.BAY,
                    breedType = BreedTypeDto.THOROUGHBRED,
                    dateOfBirth = LocalDate.of(2020, 4, 10),
                    breeder = "Coolmore",
                    microchipNumber = "392140000000002",
                    originCountry = "アイルランド",
                    landingDate = LocalDate.of(2024, 9, 1),
                    registrationNumber = "2020900001",
                )
            )

        @Test
        fun `正常な入力で 201 Created と父母不明の登録結果が返ること`() {
            val saved =
                RegisteredBloodHorse(
                    BloodHorseFixture.importedBloodHorse(),
                    BloodHorseFixture.inspection(),
                )
            every { registerImportedHorse(any<Command<RegisterImportedHorseCommand>>()) } returns
                Ok(saved)

            tester
                .post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody)
                .assertThat()
                .hasStatus(HttpStatus.CREATED)
                .bodyJson()
                .extractingPath("$.origin.country")
                .isEqualTo("アイルランド")
        }

        @Test
        fun `BlankOriginCountry で 400 と problem+json が返ること`() {
            every { registerImportedHorse(any<Command<RegisterImportedHorseCommand>>()) } returns
                Err(RegisterImportedHorseUseCaseError.BlankOriginCountry)

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
                .isEqualTo("blank-origin-country")
        }
    }

    @Nested
    inner class RegisterCarriedOverCase {
        private val uri = "/api/bloodHorses:registerCarriedOver"

        /** 父母 ID・DNA・原産国・揚陸日のいずれも持たない移行取り込みのリクエストボディ。 */
        private val validBody =
            jsonMapper.writeValueAsString(
                RegisterCarriedOverHorseRequest(
                    sex = SexDto.FEMALE,
                    coatColor = CoatColorDto.BLACK,
                    breedType = BreedTypeDto.THOROUGHBRED,
                    dateOfBirth = LocalDate.of(2002, 3, 31),
                    breeder = "ノーザンファーム",
                    microchipNumber = "392140000000003",
                    registrationNumber = "2002100501",
                )
            )

        @Test
        fun `正常な入力で 201 Created と出自 CARRIED_OVER が返ること`() {
            val saved =
                RegisteredBloodHorse(
                    BloodHorseFixture.carriedOverBloodHorse(),
                    BloodHorseFixture.inspection(),
                )
            every {
                registerCarriedOverHorse(any<Command<RegisterCarriedOverHorseCommand>>())
            } returns Ok(saved)

            tester
                .post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody)
                .assertThat()
                .hasStatus(HttpStatus.CREATED)
                .bodyJson()
                .extractingPath("$.origin.type")
                .isEqualTo("CARRIED_OVER")
        }

        @Test
        fun `BlankBreeder で 400 と problem+json が返ること`() {
            every {
                registerCarriedOverHorse(any<Command<RegisterCarriedOverHorseCommand>>())
            } returns Err(RegisterCarriedOverHorseUseCaseError.BlankBreeder)

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
                .isEqualTo("blank-breeder")
        }

        @Test
        fun `InvalidRegistrationNumber で 400 と problem+json が返ること`() {
            every {
                registerCarriedOverHorse(any<Command<RegisterCarriedOverHorseCommand>>())
            } returns Err(RegisterCarriedOverHorseUseCaseError.InvalidRegistrationNumber)

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
                .isEqualTo("invalid-registration-number")
        }

        @Test
        fun `InvalidMicrochipNumber で 400 と problem+json が返ること`() {
            every {
                registerCarriedOverHorse(any<Command<RegisterCarriedOverHorseCommand>>())
            } returns Err(RegisterCarriedOverHorseUseCaseError.InvalidMicrochipNumber)

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
                .isEqualTo("invalid-microchip-number")
        }
    }

    @Nested
    inner class GetCase {
        @Test
        fun `存在する ID で 200 OK と軽種馬リソースの完全表現が返ること`() {
            val id = UUID.fromString("11111111-1111-1111-1111-111111111111")
            val sireId = UUID.fromString("22222222-2222-2222-2222-222222222222")
            val damId = UUID.fromString("33333333-3333-3333-3333-333333333333")
            val view =
                BloodHorseDetailView(
                    id = id,
                    registrationNumber = "2023100001",
                    sex = Sex.MALE,
                    coatColor = CoatColor.BAY,
                    breedType = BreedType.THOROUGHBRED,
                    dateOfBirth = LocalDate.of(2023, 4, 10),
                    breeder = "ノーザンファーム",
                    microchipNumber = "392140000000001",
                    origin =
                        Origin.Domestic(sireId = BloodHorseId(sireId), damId = BloodHorseId(damId)),
                    name = null,
                )
            every { getBloodHorse(GetBloodHorseQuery(id)) } returns Ok(view)

            val result = tester.get().uri("/api/bloodHorses/$id").exchange()

            assertThat(result).hasStatus(HttpStatus.OK)
            assertThat(result).bodyJson().extractingPath("$.id").isEqualTo(id.toString())
            assertThat(result)
                .bodyJson()
                .extractingPath("$.registration_number")
                .isEqualTo("2023100001")
            // マイクロチップと出自を含む完全表現（サマリではない）が返ること。
            assertThat(result)
                .bodyJson()
                .extractingPath("$.microchip_number")
                .isEqualTo("392140000000001")
            assertThat(result).bodyJson().extractingPath("$.origin.type").isEqualTo("DOMESTIC")
            assertThat(result)
                .bodyJson()
                .extractingPath("$.origin.sire_id")
                .isEqualTo(sireId.toString())
        }

        @Test
        fun `存在しない ID で 404 と blood_horse_id 付きの problem+json が返ること`() {
            val id = UUID.fromString("44444444-4444-4444-4444-444444444444")
            every { getBloodHorse(GetBloodHorseQuery(id)) } returns Err(BloodHorseNotFound(id))

            val result = tester.get().uri("/api/bloodHorses/$id").exchange()

            assertThat(result)
                .hasStatus(HttpStatus.NOT_FOUND)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.error_code")
                .isEqualTo("horse-not-found")
            assertThat(result)
                .bodyJson()
                .extractingPath("$.blood_horse_id")
                .isEqualTo(id.toString())
        }
    }
}
