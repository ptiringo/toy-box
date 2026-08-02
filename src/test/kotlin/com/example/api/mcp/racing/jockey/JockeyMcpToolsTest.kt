package com.example.api.mcp.racing.jockey

import com.example.api.application.racing.jockey.GetJockeyQuery
import com.example.api.application.racing.jockey.GetJockeyUseCase
import com.example.api.application.racing.jockey.JockeyNotFound
import com.example.api.application.racing.jockey.JockeyView
import com.example.api.domain.shared.generateId
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * MCP アダプタ [JockeyMcpTools] の slice 相当ユニットテスト。
 *
 * adapter リングのため [GetJockeyUseCase] を mockk でスタブし、Result → MCP ツール結果（成功 DTO /
 * 失敗時の例外送出）への変換のみを検証する（testing.md: adapter は slice 相当で入出力変換を検証）。
 */
class JockeyMcpToolsTest {
    private val getJockeyUseCase = mockk<GetJockeyUseCase>()
    private val tools = JockeyMcpTools(getJockeyUseCase)

    @Test
    fun `存在するIDならJockeyMcpResultを返す`() {
        val id = generateId()
        every { getJockeyUseCase(GetJockeyQuery(id)) } returns
            Ok(JockeyView(id = id, firstName = "武", lastName = "豊"))

        val result = tools.getJockey(id.toString())

        assert(result == JockeyMcpResult(id = id.toString(), firstName = "武", lastName = "豊"))
    }

    @Test
    fun `存在しないIDなら例外を送出する（MCPのisErrorへ写す）`() {
        val id = generateId()
        every { getJockeyUseCase(GetJockeyQuery(id)) } returns Err(JockeyNotFound(id))

        val ex = assertThrows<NoSuchElementException> { tools.getJockey(id.toString()) }
        assert(ex.message == "jockey not found: $id")
    }

    @Test
    fun `不正なUUID文字列なら例外を送出する`() {
        assertThrows<IllegalArgumentException> { tools.getJockey("not-a-uuid") }
    }
}
