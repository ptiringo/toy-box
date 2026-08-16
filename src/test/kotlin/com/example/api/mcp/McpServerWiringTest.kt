package com.example.api.mcp

import com.example.api.mcp.iam.world.WorldMcpTools
import com.example.api.mcp.racing.jockey.JockeyMcpTools
import com.example.api.support.PostgresContainerSupport
import io.modelcontextprotocol.server.McpServerFeatures
import io.modelcontextprotocol.server.McpSyncServer
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.core.ResolvableType
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
 * ツール Bean の `@Autowired` 到達性（`isInitialized`）だけでは「`@Component` が `local` で Bean 化されたか」＝ `@Profile`
 * の再確認にしかならず、`@McpTool` が実際に annotation scanner に走査されて MCP サーバーへ 登録されたことは検証できない（走査が壊れても・ツール名を typo
 * しても緑のまま通る）。そこで Spring AI の autoconfigure（`McpServerSpecificationFactoryAutoConfiguration`）が公開する
 * `List<McpServerFeatures.SyncToolSpecification>` Bean を注入し、公開ツール名の集合を直接検証する。
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
    @Autowired private lateinit var mcpProperties: McpProperties

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

    @Test
    fun `localプロファイルではtoy-boxmcpsubject-idの束縛結果が期待どおりになる`() {
        // 本プロジェクト初の @ConfigurationProperties 利用のため、prefix やキー名の綴りが壊れると
        // 気付くのが「開発者がローカルで MCP を呼んだ瞬間」になり、しかもエラーメッセージが
        // 「MCP_SUBJECT_ID を渡してください」（渡しているのに）という誤誘導になる。ここで束縛結果を直接確認する。
        assert(mcpProperties.subjectId == "wiring-test-subject")
    }

    @Test
    fun `localプロファイルではlist_worldsとget_jockeyがMCPツールとして公開される`() {
        val toolNames = toolSpecificationNames(context)

        assert(toolNames.contains("list_worlds"))
        assert(toolNames.contains("get_jockey"))
    }

    @Test
    fun `公開する全MCPツールの引数に説明が付いている`() {
        // 引数の説明はツールを増やすたびに書き忘れうるうえ、抜けても配線は壊れない（スキーマが
        // 引数名と型だけになり、LLM が「何を入れればよいか」を推測する劣化として静かに出る）。
        // アノテーションの有無ではなく、実際に MCP クライアントへ届く入力スキーマ側で確認する（#758）。
        val undocumented = undocumentedToolParameters(context)

        assert(undocumented.isEmpty())
    }

    companion object {
        /**
         * Spring AI が公開する `List<McpServerFeatures.SyncToolSpecification>` Bean を取り出す。
         * ジェネリクスを保った検索のため `getBeanNamesForType` ではなく [ResolvableType] を使った
         * [ApplicationContext.getBeanProvider] を使う（bean 名は Spring AI の内部実装詳細のため依存しない）。
         *
         * 同一型の Bean が 2 つ存在する（`@McpTool` 走査由来の `toolSpecs` と、`ToolCallback` 由来の `syncTools`。
         * 本プロジェクトは `@McpTool` のみを使うため後者は常に空リストだが、実配線では
         * [io.modelcontextprotocol.server.McpSyncServer] が両方を合算して使う実装のため、テストも `ifAvailable`（単一 Bean
         * 前提）ではなく全 Bean を合算する）。
         */
        fun toolSpecifications(
            context: ApplicationContext
        ): List<McpServerFeatures.SyncToolSpecification> {
            val toolSpecsType =
                ResolvableType.forClassWithGenerics(
                    List::class.java,
                    McpServerFeatures.SyncToolSpecification::class.java,
                )
            return context
                .getBeanProvider<List<McpServerFeatures.SyncToolSpecification>>(toolSpecsType)
                .orderedStream()
                .toList()
                .flatten()
        }

        /** [toolSpecifications] が公開するツール名の集合。 */
        fun toolSpecificationNames(context: ApplicationContext): Set<String> =
            toolSpecifications(context).map { it.tool().name() }.toSet()

        /**
         * 公開ツールの入力スキーマを走査し、説明の無い引数を `<ツール名>.<引数名>` の形で拾う。 引数を持たないツール（`list_worlds`）は `properties`
         * が空なので自然に対象外になる。
         */
        fun undocumentedToolParameters(context: ApplicationContext): List<String> =
            toolSpecifications(context).flatMap { spec ->
                val properties =
                    spec.tool().inputSchema()["properties"] as? Map<*, *> ?: emptyMap<Any, Any>()
                properties
                    .filterValues { schema ->
                        ((schema as? Map<*, *>)?.get("description") as? String).isNullOrBlank()
                    }
                    .keys
                    .map { name -> "${spec.tool().name()}.$name" }
            }
    }
}
