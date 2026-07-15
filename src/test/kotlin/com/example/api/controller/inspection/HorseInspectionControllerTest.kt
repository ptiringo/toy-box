package com.example.api.controller.inspection

import com.example.api.application.iam.account.ResolveActorUseCase
import com.example.api.application.studbook.inspection.FindHorseInspectionQuery
import com.example.api.application.studbook.inspection.FindHorseInspectionUseCase
import com.example.api.application.studbook.inspection.HorseInspectionNotFound
import com.example.api.application.studbook.inspection.HorseInspectionView
import com.example.api.application.studbook.inspection.RecordHorseInspectionCommand
import com.example.api.application.studbook.inspection.RecordHorseInspectionUseCase
import com.example.api.application.studbook.inspection.RecordHorseInspectionUseCaseError
import com.example.api.config.ClockConfiguration
import com.example.api.controller.horse.DnaParentageResultDto
import com.example.api.controller.inspection.request.RecordHorseInspectionRequest
import com.example.api.domain.shared.AccountId
import com.example.api.domain.shared.Actor
import com.example.api.domain.shared.Command
import com.example.api.domain.shared.generateId
import com.example.api.domain.studbook.model.StudbookPermissions
import com.example.api.domain.studbook.model.inspection.DnaParentageResult
import com.example.api.domain.studbook.model.inspection.HorseInspection
import com.example.api.domain.studbook.model.inspection.IdentificationFeatures
import com.example.api.domain.studbook.model.inspection.MicrochipNumber
import com.example.api.domain.studbook.model.inspection.ParentageDetermination
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.slot
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
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

@WebMvcTest(HorseInspectionController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ClockConfiguration::class)
@TestConstructor(autowireMode = AutowireMode.ALL)
class HorseInspectionControllerTest(val mockMvc: MockMvc, val jsonMapper: JsonMapper) {
    // WebMvcConfig 経由の ActorArgumentResolver が全 @WebMvcTest slice で自動検出されるための必須モック。
    @MockkBean private lateinit var resolveActor: ResolveActorUseCase

    @MockkBean private lateinit var recordHorseInspection: RecordHorseInspectionUseCase
    @MockkBean private lateinit var findHorseInspection: FindHorseInspectionUseCase

    private val tester = MockMvcTester.create(mockMvc)

    @BeforeEach
    fun stubResolveActor() {
        every { resolveActor(any()) } returns
            Ok(Actor(AccountId(UUID.randomUUID()), setOf(StudbookPermissions.INSPECTION_RECORD)))
    }

    /** DNA 判定＋特徴記述子つきの審査集約を組む（レスポンス表現の検証用）。 */
    private fun inspectionWithFeatures(): HorseInspection =
        HorseInspection.create(
            microchipNumber = MicrochipNumber.create("392140000000001").unwrap(),
            parentage = ParentageDetermination.ByDna(DnaParentageResult.CONSISTENT),
            features = IdentificationFeatures("頭部正中", "左後一白", null),
        )

    @Nested
    inner class RecordCase {
        private val uri = "/api/horseInspections"

        /** DTO を実アプリと同じ [jsonMapper] でシリアライズし、契約とテストの二重管理を避ける。 */
        private val validBody =
            jsonMapper.writeValueAsString(
                RecordHorseInspectionRequest(
                    microchipNumber = "392140000000001",
                    parentage = ParentageDeterminationDto.ByDna(DnaParentageResultDto.CONSISTENT),
                    features =
                        IdentificationFeaturesDto(
                            hairWhorl = "頭部正中",
                            whiteMarkings = "左後一白",
                            nosePrint = null,
                        ),
                )
            )

        @Test
        fun `正常な入力で 201 Created と DNA 判定つきの審査リソースが返ること`() {
            every {
                recordHorseInspection(any(), any<Command<RecordHorseInspectionCommand>>())
            } returns Ok(inspectionWithFeatures())

            tester
                .post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody)
                .assertThat()
                .hasStatus(HttpStatus.CREATED)
                .bodyJson()
                .extractingPath("$.parentage.dna_parentage_result")
                .isEqualTo("CONSISTENT")
        }

        @Test
        fun `判定対象外（フィールドなしバリアント）の審査を記録でき features 未指定は null になること`() {
            val saved =
                HorseInspection.create(
                    microchipNumber = MicrochipNumber.create("392140000000002").unwrap(),
                    parentage = ParentageDetermination.NotApplicable,
                )
            every {
                recordHorseInspection(any(), any<Command<RecordHorseInspectionCommand>>())
            } returns Ok(saved)

            tester
                .post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                        "microchip_number": "392140000000002",
                        "parentage": { "type": "NOT_APPLICABLE" }
                    }
                    """
                        .trimIndent()
                )
                .assertThat()
                .hasStatus(HttpStatus.CREATED)
                .bodyJson()
                .extractingPath("$.features")
                .isNull()
        }

        @Test
        fun `全フィールド null の features はコマンドで不在（null）へ正規化されること`() {
            val command = slot<Command<RecordHorseInspectionCommand>>()
            every { recordHorseInspection(any(), capture(command)) } returns
                Ok(inspectionWithFeatures())

            tester
                .post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                        "microchip_number": "392140000000001",
                        "parentage": { "type": "BY_BLOOD_TYPE" },
                        "features": {
                            "hair_whorl": null,
                            "white_markings": null,
                            "nose_print": null
                        }
                    }
                    """
                        .trimIndent()
                )
                .assertThat()
                .hasStatus(HttpStatus.CREATED)

            assert(command.captured.payload.features == null)
        }

        @Test
        fun `InvalidMicrochipNumber で 400 と problem+json が返ること`() {
            every {
                recordHorseInspection(any(), any<Command<RecordHorseInspectionCommand>>())
            } returns Err(RecordHorseInspectionUseCaseError.InvalidMicrochip)

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

        @Test
        fun `未知の parentage type は Jackson デシリアライズで弾かれ 400 が返ること`() {
            tester
                .post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                        "microchip_number": "392140000000001",
                        "parentage": { "type": "BY_RUMOR" }
                    }
                    """
                        .trimIndent()
                )
                .assertThat()
                .hasStatus(HttpStatus.BAD_REQUEST)
        }
    }

    @Nested
    inner class GetCase {
        @Test
        fun `存在する ID で 200 OK と審査リソースが返ること`() {
            val id = generateId()
            val view =
                HorseInspectionView(
                    id = id,
                    microchipNumber = "392140000000001",
                    parentage = ParentageDetermination.ByDna(DnaParentageResult.CONSISTENT),
                    features = IdentificationFeatures("頭部正中", null, null),
                )
            every { findHorseInspection(FindHorseInspectionQuery(id)) } returns Ok(view)

            tester
                .get()
                .uri("/api/horseInspections/$id")
                .assertThat()
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .extractingPath("$.microchip_number")
                .isEqualTo("392140000000001")
        }

        @Test
        fun `存在しない ID で 404 と inspection_id 付きの problem+json が返ること`() {
            val id = UUID.fromString("44444444-4444-4444-4444-444444444444")
            every { findHorseInspection(FindHorseInspectionQuery(id)) } returns
                Err(HorseInspectionNotFound(id))

            val result = tester.get().uri("/api/horseInspections/$id").exchange()

            assertThat(result)
                .hasStatus(HttpStatus.NOT_FOUND)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.error_code")
                .isEqualTo("horse-inspection-not-found")
            assertThat(result).bodyJson().extractingPath("$.inspection_id").isEqualTo(id.toString())
        }
    }
}
