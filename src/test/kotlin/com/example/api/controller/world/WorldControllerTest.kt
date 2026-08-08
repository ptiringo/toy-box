package com.example.api.controller.world

import com.example.api.application.iam.world.CreateWorldError
import com.example.api.application.iam.world.CreateWorldUseCase
import com.example.api.application.iam.world.DeleteWorldUseCase
import com.example.api.application.iam.world.ListWorldsUseCase
import com.example.api.application.iam.world.RenameWorldUseCase
import com.example.api.application.iam.world.WorldMutationError
import com.example.api.application.iam.world.WorldQueries
import com.example.api.application.iam.world.WorldView
import com.example.api.config.ClockConfiguration
import com.example.api.domain.iam.model.account.AccountFixture
import com.example.api.domain.iam.model.account.AccountRepository
import com.example.api.domain.iam.model.account.SubjectId
import com.example.api.domain.iam.model.world.WorldNameValidationError
import com.example.api.domain.shared.WorldId
import com.example.api.domain.shared.generateId
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.test.context.TestConstructor
import org.springframework.test.context.TestConstructor.AutowireMode
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.assertj.MockMvcTester

/**
 * 世界リソースの HTTP 契約を検証するスライステスト。
 *
 * `WorldController` は全ハンドラで `@CurrentAccount` を使うため、`CurrentAccountArgumentResolverIntegrationTest`
 * と同じ実解決経路（`SecurityContextHolder` に検証済み JWT を積み、`AccountRepository` をスタブ）で `accountId`
 * を解決させる。ブリーフでは `CurrentAccountArgumentResolver` 自体を `@MockkBean` で差し替える 案だったが、既存 slice
 * テスト（`JockeyControllerTest` / `BloodHorseControllerTest`）はいずれも本物の resolver をそのまま使い
 * `AccountRepository` だけをスタブする流儀のため、そちらに合わせた。
 */
@WebMvcTest(WorldController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ClockConfiguration::class)
@TestConstructor(autowireMode = AutowireMode.ALL)
class WorldControllerTest(val mockMvc: MockMvc) {
    @MockkBean private lateinit var listWorlds: ListWorldsUseCase
    @MockkBean private lateinit var createWorld: CreateWorldUseCase
    @MockkBean private lateinit var renameWorld: RenameWorldUseCase
    @MockkBean private lateinit var deleteWorld: DeleteWorldUseCase
    @MockkBean private lateinit var accounts: AccountRepository
    @MockkBean private lateinit var worldQueries: WorldQueries

    private val tester = MockMvcTester.create(mockMvc)
    private val subject = "world-controller-test-subject"
    private val account = AccountFixture.account(subjectId = subject)

    /** `@CurrentAccount` が実解決経路で [account] の ID を返すよう、認証コンテキストを用意する。 */
    @BeforeEach
    fun stubCurrentAccount() {
        every { accounts.findBySubjectId(SubjectId(subject)) } returns account
        val jwt =
            Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(subject)
                .claim("sub", subject)
                .build()
        SecurityContextHolder.getContext().authentication = JwtAuthenticationToken(jwt)
    }

    /** 他テストへの `SecurityContextHolder`（ThreadLocal）の漏れを防ぐ。 */
    @AfterEach
    fun clearSecurityContext() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `世界一覧は自分の世界を返す`() {
        val worldId = generateId()
        every { listWorlds(any()) } returns listOf(WorldView(worldId, "はじまりの世界"))

        tester
            .get()
            .uri("/api/worlds")
            .assertThat()
            .hasStatusOk()
            .bodyJson()
            .extractingPath("$[0].name")
            .isEqualTo("はじまりの世界")
    }

    @Test
    fun `世界を作ると 201 とリソース表現を返す`() {
        val worldId = generateId()
        every { createWorld(any(), any()) } returns Ok(WorldView(worldId, "二つ目の牧場"))

        tester
            .post()
            .uri("/api/worlds")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"name":"二つ目の牧場"}""")
            .assertThat()
            .hasStatus(HttpStatus.CREATED)
            .bodyJson()
            .extractingPath("$.name")
            .isEqualTo("二つ目の牧場")
    }

    @Test
    fun `名前がブランクなら 400 の problem を返す`() {
        every { createWorld(any(), any()) } returns
            Err(CreateWorldError.InvalidName(WorldNameValidationError.Blank))

        tester
            .post()
            .uri("/api/worlds")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"name":" "}""")
            .assertThat()
            .hasStatus(HttpStatus.BAD_REQUEST)
            .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
    }

    @Test
    fun `他人の世界を改名しようとすると 404 の problem を返す`() {
        val worldId = generateId()
        every { renameWorld(any(), any()) } returns
            Err(WorldMutationError.NotFound(WorldId(worldId)))

        tester
            .patch()
            .uri("/api/worlds/$worldId")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"name":"乗っ取り"}""")
            .assertThat()
            .hasStatus(HttpStatus.NOT_FOUND)
            .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
    }

    @Test
    fun `世界を削除すると 204 を返す`() {
        val worldId = generateId()
        every { deleteWorld(any(), any()) } returns Ok(Unit)

        tester.delete().uri("/api/worlds/$worldId").assertThat().hasStatus(HttpStatus.NO_CONTENT)
    }
}
