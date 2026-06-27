package com.example.api.mcp

import com.example.api.mcp.racing.jockey.JockeyMcpTools
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/**
 * MCP アダプタの配線確認（最小 `@SpringBootTest`）。
 *
 * Spring AI MCP server オートコンフィグを足してもアプリケーションコンテキストが起動し、`@McpTool` を持つ [JockeyMcpTools] Bean
 * が登録されることだけを確認する。MCP プロトコルの E2E（ツール一覧・呼び出し）は follow-up とし、ロジック網羅は slice
 * テスト（JockeyMcpToolsTest）で済ませる（testing.md: 統合は最小限）。
 */
@SpringBootTest
class McpServerWiringTest {
    @Autowired private lateinit var jockeyMcpTools: JockeyMcpTools

    @Test
    fun `コンテキストが起動しMCPツールBeanが登録される`() {
        // Power Assert は isInitialized を直接 assert() に渡すと IR lowering で失敗するため、
        // 先に Boolean へ評価してから assert に渡す。
        val initialized = this::jockeyMcpTools.isInitialized
        assert(initialized)
    }
}
