package com.example.api.mcp

import com.example.api.mcp.iam.world.WorldMcpTools
import com.example.api.mcp.racing.jockey.JockeyMcpTools
import com.example.api.support.PostgresContainerSupport
import io.modelcontextprotocol.server.McpSyncServer
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext

/**
 * 既定プロファイルでは MCP が 1 つも生えないことの検証（#712）。
 *
 * 「MCP は開発者自身のローカル探索ツールであり、本番・CI では口ごと開かない」が本設計の要であるため、 意図の表明（設定コメント）で終わらせず機械で押さえる。守りは 2
 * 枚あり、片方だけでは片方が誤って 有効化されても気付けないため、両方を別々に検証する:
 * - `@Profile("local")`（Task 1〜3。ツール Bean 自体を生やさない）: [JockeyMcpTools] / [WorldMcpTools] /
 *   [McpActorFactory] が対象
 * - `spring.ai.mcp.server.enabled: false`（本タスク。MCP サーバー本体・HTTP エンドポイントを落とす）: `McpSyncServer`（Spring
 *   AI の MCP server オートコンフィグが登録するサーバー本体の Bean）が対象
 *
 * `@Autowired` の失敗（コンテキスト起動エラー）に頼らず Bean 名の集合を直接見るのは、 「起動が壊れないこと」も同時に確かめたいため。
 *
 * `@SpringBootTest` は [com.example.api.ApiApplicationTests] と同一構成にしてコンテキストキャッシュを共有する
 * （プロファイルもプロパティも足さない）。
 */
@SpringBootTest
class McpDisabledByDefaultTest : PostgresContainerSupport() {
    @Autowired private lateinit var context: ApplicationContext

    @Test
    fun `既定プロファイルではMCPツールBeanが登録されない`() {
        val jockeyTools = context.getBeanNamesForType(JockeyMcpTools::class.java)
        val worldTools = context.getBeanNamesForType(WorldMcpTools::class.java)

        assert(jockeyTools.isEmpty())
        assert(worldTools.isEmpty())
    }

    @Test
    fun `既定プロファイルでもActor解決のBeanは生えない`() {
        val factories = context.getBeanNamesForType(McpActorFactory::class.java)

        assert(factories.isEmpty())
    }

    @Test
    fun `既定プロファイルではMCPサーバー本体のBeanも登録されない`() {
        val mcpServers = context.getBeanNamesForType(McpSyncServer::class.java)

        assert(mcpServers.isEmpty())
    }

    @Test
    fun `既定プロファイルでは公開MCPツールが1つも無い`() {
        // McpServerWiringTest の「list_worlds / get_jockey が公開ツール名に含まれる」と対称の検証。
        // ツール Bean（JockeyMcpTools 等）自体が @Profile("local") で生えない（上のテスト）だけでなく、
        // Spring AI が走査して組み立てる公開ツール一覧も空であることを確認する。
        val toolNames = McpServerWiringTest.toolSpecificationNames(context)

        assert(toolNames.isEmpty())
    }
}
