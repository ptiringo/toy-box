package com.example.api.mcp.iam.world

import com.example.api.application.iam.world.ListWorldsQuery
import com.example.api.application.iam.world.ListWorldsUseCase
import com.example.api.application.iam.world.WorldView
import com.example.api.domain.shared.AccountId
import com.example.api.domain.shared.generateId
import com.example.api.mcp.McpActorFactory
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test

/**
 * MCP アダプタ [WorldMcpTools] の slice 相当ユニットテスト。
 *
 * adapter リングのため [ListWorldsUseCase] と [McpActorFactory] を mockk でスタブし、読みモデル （[WorldView]）→ MCP
 * ツール結果への変換のみを検証する。
 */
class WorldMcpToolsTest {
    private val actors = mockk<McpActorFactory>()
    private val listWorldsUseCase = mockk<ListWorldsUseCase>()
    private val tools = WorldMcpTools(actors, listWorldsUseCase)

    private val accountId = AccountId(generateId())

    @Test
    fun `自分の世界を WorldMcpResult のリストへ写す`() {
        val first = generateId()
        val second = generateId()
        every { actors.resolveAccountId() } returns accountId
        every { listWorldsUseCase(ListWorldsQuery(accountId)) } returns
            listOf(WorldView(id = first, name = "はじまりの世界"), WorldView(id = second, name = "やり直し牧場"))

        val result = tools.listWorlds()

        assert(
            result ==
                listOf(
                    WorldMcpResult(id = first.toString(), name = "はじまりの世界"),
                    WorldMcpResult(id = second.toString(), name = "やり直し牧場"),
                )
        )
    }

    @Test
    fun `世界が無ければ空リストを返す`() {
        every { actors.resolveAccountId() } returns accountId
        every { listWorldsUseCase(ListWorldsQuery(accountId)) } returns emptyList()

        val result = tools.listWorlds()

        assert(result.isEmpty())
    }
}
