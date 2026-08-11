package com.example.api.mcp.iam.world

/**
 * MCP ツール `list_worlds` の結果表現（adapter 所有のワイヤ DTO）。
 *
 * application の読みモデル [com.example.api.application.iam.world.WorldView]（`@QueryModel`）をアダプタ境界の
 * 外へ漏らさないため、ここで素の DTO へ写す（controller の `WorldResponse` と同じ役割）。id はワイヤ上は文字列で扱う。
 *
 * @property id 世界の ID（`get_jockey` 等の `worldId` 引数にそのまま渡せる）
 * @property name 世界の名前
 */
data class WorldMcpResult(val id: String, val name: String)
