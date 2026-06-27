package com.example.api.mcp.racing.jockey

/**
 * MCP ツール `find_jockey` の結果表現（adapter 所有のワイヤ DTO）。
 *
 * application の読みモデル [com.example.api.application.racing.jockey.JockeyView]（`@QueryModel`）を
 * アダプタ境界の外へ漏らさないため、ここで素の DTO へ写す（controller の `JockeyResponse` と同じ役割）。 id はワイヤ上は文字列で扱う。
 */
data class JockeyMcpResult(val id: String, val firstName: String, val lastName: String)
