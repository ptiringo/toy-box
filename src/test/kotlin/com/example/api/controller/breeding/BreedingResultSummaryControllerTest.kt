package com.example.api.controller.breeding

import com.example.api.application.iam.world.WorldQueries
import com.example.api.application.studbook.breeding.BreedingResultSummaryView
import com.example.api.application.studbook.breeding.ListBreedingResultSummariesQuery
import com.example.api.application.studbook.breeding.ListBreedingResultSummariesUseCase
import com.example.api.controller.ActorArgumentResolver
import com.example.api.domain.iam.model.account.AccountRepository
import com.example.api.domain.shared.AccountId
import com.example.api.domain.shared.Actor
import com.example.api.domain.shared.WorldId
import com.example.api.domain.shared.generateId
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import java.util.UUID
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.HttpStatus
import org.springframework.test.context.TestConstructor
import org.springframework.test.context.TestConstructor.AutowireMode
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.assertj.MockMvcTester

@WebMvcTest(BreedingResultSummaryController::class)
@AutoConfigureMockMvc(addFilters = false)
@TestConstructor(autowireMode = AutowireMode.ALL)
class BreedingResultSummaryControllerTest(val mockMvc: MockMvc) {
    @MockkBean private lateinit var listBreedingResultSummaries: ListBreedingResultSummariesUseCase

    // WebMvcConfig（CurrentAccountArgumentResolver）が全 @WebMvcTest スライスへ自動で載るため必要（本テストの検証対象ではない）。
    @MockkBean private lateinit var accounts: AccountRepository
    @MockkBean private lateinit var worldQueries: WorldQueries

    @MockkBean private lateinit var actorArgumentResolver: ActorArgumentResolver

    private val actor = Actor(accountId = AccountId(generateId()), worldId = WorldId(generateId()))

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

    private val stallionId = UUID.fromString("11111111-1111-1111-1111-111111111111")

    @Test
    fun `種牡馬IDの集計一覧が200で件数と率つきの配列で返ること`() {
        // BreedingResultSummaryView.of(stallionId, 2024, 6, 4, 1) → 受胎率 4/6=66.7%
        every {
            listBreedingResultSummaries(any<Actor>(), any<ListBreedingResultSummariesQuery>())
        } returns listOf(BreedingResultSummaryView.of(stallionId, 2024, 6, 4, 1))

        val body =
            tester
                .get()
                .uri("/api/breedingResultSummaries?stallionId=$stallionId")
                .assertThat()
                .hasStatus(HttpStatus.OK)
                .bodyJson()

        body.extractingPath("$[0].breeding_year").isEqualTo(2024)
        // 受胎率が JSON 数値 66.7 として公開されること
        body.extractingPath("$[0].conception_rate").isEqualTo(66.7)
    }

    @Test
    fun `集計の件数フィールドが snake_case で公開されること`() {
        every {
            listBreedingResultSummaries(any<Actor>(), any<ListBreedingResultSummariesQuery>())
        } returns listOf(BreedingResultSummaryView.of(stallionId, 2024, 6, 4, 1))

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
        every {
            listBreedingResultSummaries(any<Actor>(), any<ListBreedingResultSummariesQuery>())
        } returns emptyList()

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
