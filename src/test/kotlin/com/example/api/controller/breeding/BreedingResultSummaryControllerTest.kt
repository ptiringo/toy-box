package com.example.api.controller.breeding

import com.example.api.application.studbook.breeding.BreedingResultSummaryView
import com.example.api.application.studbook.breeding.FindBreedingResultSummaryQuery
import com.example.api.application.studbook.breeding.FindBreedingResultSummaryUseCase
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import java.util.UUID
import org.junit.jupiter.api.Test
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.HttpStatus
import org.springframework.test.context.TestConstructor
import org.springframework.test.context.TestConstructor.AutowireMode
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.assertj.MockMvcTester

@WebMvcTest(BreedingResultSummaryController::class)
@TestConstructor(autowireMode = AutowireMode.ALL)
class BreedingResultSummaryControllerTest(val mockMvc: MockMvc) {
    @MockkBean private lateinit var findBreedingResultSummary: FindBreedingResultSummaryUseCase

    private val tester = MockMvcTester.create(mockMvc)

    private val stallionId = UUID.fromString("11111111-1111-1111-1111-111111111111")

    @Test
    fun `種牡馬IDの集計一覧が200で件数と率つきの配列で返ること`() {
        every { findBreedingResultSummary(any<FindBreedingResultSummaryQuery>()) } returns
            listOf(BreedingResultSummaryView.of(stallionId, 2024, 6, 4, 1))

        tester
            .get()
            .uri("/api/breedingResultSummaries?stallionId=$stallionId")
            .assertThat()
            .hasStatus(HttpStatus.OK)
            .bodyJson()
            .extractingPath("$[0].breeding_year")
            .isEqualTo(2024)
    }

    @Test
    fun `集計の件数フィールドが snake_case で公開されること`() {
        every { findBreedingResultSummary(any<FindBreedingResultSummaryQuery>()) } returns
            listOf(BreedingResultSummaryView.of(stallionId, 2024, 6, 4, 1))

        tester
            .get()
            .uri("/api/breedingResultSummaries?stallionId=$stallionId")
            .assertThat()
            .hasStatusOk()
            .bodyJson()
            .extractingPath("$[0].mares_covered")
            .isEqualTo(6)
    }

    @Test
    fun `該当なしは200で空配列を返すこと`() {
        every { findBreedingResultSummary(any<FindBreedingResultSummaryQuery>()) } returns
            emptyList()

        tester
            .get()
            .uri("/api/breedingResultSummaries?stallionId=$stallionId")
            .assertThat()
            .hasStatusOk()
            .bodyJson()
            .extractingPath("$")
            .asArray()
            .isEmpty()
    }

    @Test
    fun `stallionId が欠けると400が返ること`() {
        tester
            .get()
            .uri("/api/breedingResultSummaries")
            .assertThat()
            .hasStatus(HttpStatus.BAD_REQUEST)
    }
}
