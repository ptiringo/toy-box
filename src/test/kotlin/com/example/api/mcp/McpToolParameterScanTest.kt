package com.example.api.mcp

import com.example.api.mcp.McpServerWiringTest.Companion.accountLikeParameters
import org.junit.jupiter.api.Test

/**
 * MCP ツールの入力スキーマ走査（[McpServerWiringTest] の `accountId` 相当の検査、#752）が、違反するスキーマを実際に 検出することを確かめる。
 *
 * 配線テスト側は現状のツールが違反しないことしか見ないため、走査が空振りしていても緑のまま通る。ここで違反サンプルを与えて 検出側を常設で担保する。
 */
class McpToolParameterScanTest {
    private fun schema(vararg parameterNames: String): Map<String, Any> =
        mapOf(
            "type" to "object",
            "properties" to parameterNames.associateWith { mapOf("type" to "string") },
        )

    @Test
    fun `accountIdやsubjectIdを引数に持つツールを検出すること`() {
        val found =
            accountLikeParameters(
                mapOf(
                    "get_jockey" to schema("worldId", "jockeyId"),
                    "bad_tool" to schema("worldId", "accountId"),
                    "snake_tool" to schema("account_id"),
                    "subject_tool" to schema("subjectId"),
                )
            )

        assert(
            found == listOf("bad_tool.accountId", "snake_tool.account_id", "subject_tool.subjectId")
        )
    }

    @Test
    fun `worldIdなどアカウントを指定しない引数だけのツールは検出しないこと`() {
        val found =
            accountLikeParameters(
                mapOf("get_jockey" to schema("worldId", "jockeyId"), "list_worlds" to schema())
            )

        assert(found.isEmpty())
    }
}
