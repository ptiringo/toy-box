package com.example.api.mcp.racing.jockey

import com.example.api.application.racing.jockey.FindJockeyQuery
import com.example.api.application.racing.jockey.FindJockeyUseCase
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
 * adapter リングのため [FindJockeyUseCase] を mockk でスタブし、Result → MCP ツール結果（成功 DTO /
 * 失敗時の例外送出）への変換のみを検証する（testing.md: adapter は slice 相当で入出力変換を検証）。
 */
class JockeyMcpToolsTest {
    private val findJockeyUseCase = mockk<FindJockeyUseCase>()
    private val tools = JockeyMcpTools(findJockeyUseCase)

    @Test
    fun `存在するIDならJockeyMcpResultを返す`() {
        val id = generateId()
        every { findJockeyUseCase(FindJockeyQuery(id)) } returns
            Ok(JockeyView(id = id, firstName = "武", lastName = "豊"))

        val result = tools.findJockey(id.toString())

        assert(result == JockeyMcpResult(id = id.toString(), firstName = "武", lastName = "豊"))
    }

    @Test
    fun `存在しないIDなら例外を送出する（MCPのisErrorへ写す）`() {
        val id = generateId()
        every { findJockeyUseCase(FindJockeyQuery(id)) } returns Err(JockeyNotFound(id))

        val ex = assertThrows<NoSuchElementException> { tools.findJockey(id.toString()) }
        assert(ex.message == "jockey not found: $id")
    }

    @Test
    fun `不正なUUID文字列なら例外を送出する`() {
        assertThrows<IllegalArgumentException> { tools.findJockey("not-a-uuid") }
    }
}
