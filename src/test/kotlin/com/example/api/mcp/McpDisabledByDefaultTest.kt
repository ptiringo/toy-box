package com.example.api.mcp

import com.example.api.mcp.iam.world.WorldMcpTools
import com.example.api.mcp.racing.jockey.JockeyMcpTools
import com.example.api.support.PostgresContainerSupport
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext

/**
 * 既定プロファイルでは MCP ツールが 1 つも生えないことの検証（#712）。
 *
 * 「MCP は開発者自身のローカル探索ツールであり、本番・CI では口ごと開かない」が本設計の要であるため、 意図の表明（設定コメント）で終わらせず機械で押さえる。`@Autowired`
 * の失敗（コンテキスト起動エラー）に 頼らず Bean 名の集合を直接見るのは、「起動が壊れないこと」も同時に確かめたいため。
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
}
