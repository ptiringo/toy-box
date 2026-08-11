package com.example.api.mcp

import com.example.api.mcp.iam.world.WorldMcpTools
import com.example.api.mcp.racing.jockey.JockeyMcpTools
import com.example.api.support.PostgresContainerSupport
import io.modelcontextprotocol.server.McpSyncServer
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource

/**
 * MCP アダプタの配線確認（最小 `@SpringBootTest`）。
 *
 * `local` プロファイルで Spring AI MCP server オートコンフィグを足してもアプリケーションコンテキストが 起動し、`@McpTool` を持つ Bean
 * が登録されることだけを確認する。MCP プロトコルの E2E（ツール一覧・呼び出し） は follow-up とし、ロジック網羅は slice
 * テスト（[com.example.api.mcp.racing.jockey.JockeyMcpToolsTest] 等）で済ませる（testing.md: 統合は最小限）。
 *
 * `McpSyncServer`（MCP サーバー本体。`spring.ai.mcp.server.enabled: true` が効いていることの検証点）が
 * 登録されることも合わせて確認する。既定プロファイルで生えないことは [McpDisabledByDefaultTest] が 対称に検証する。
 *
 * `subject-id` を固定値で与えるのは、Bean の登録確認に実 DB のアカウントを要求しないため。 [McpActorFactory] は Bean 生成時ではなくツール呼び出し時に
 * subject を解決するので、これで成立する。 datasource は外部供給（H2 全面脱却・#451）のため [PostgresContainerSupport] を継承する。
 */
@SpringBootTest
@ActiveProfiles("local")
@TestPropertySource(properties = ["toy-box.mcp.subject-id=wiring-test-subject"])
class McpServerWiringTest : PostgresContainerSupport() {
    @Autowired private lateinit var context: ApplicationContext
    @Autowired private lateinit var jockeyMcpTools: JockeyMcpTools
    @Autowired private lateinit var worldMcpTools: WorldMcpTools

    @Test
    fun `localプロファイルではコンテキストが起動しMCPツールBeanが登録される`() {
        // Power Assert は isInitialized を直接 assert() に渡すと IR lowering で失敗するため、
        // 先に Boolean へ評価してから assert に渡す。
        val jockeyInitialized = this::jockeyMcpTools.isInitialized
        val worldInitialized = this::worldMcpTools.isInitialized

        assert(jockeyInitialized)
        assert(worldInitialized)
    }

    @Test
    fun `localプロファイルではMCPサーバー本体のBeanも登録される`() {
        val mcpServers = context.getBeanNamesForType(McpSyncServer::class.java)

        assert(mcpServers.isNotEmpty())
    }
}
