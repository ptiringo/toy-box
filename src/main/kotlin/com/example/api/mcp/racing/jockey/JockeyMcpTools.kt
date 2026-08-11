package com.example.api.mcp.racing.jockey

import com.example.api.application.racing.jockey.GetJockeyQuery
import com.example.api.application.racing.jockey.GetJockeyUseCase
import com.example.api.mcp.McpActorFactory
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.map
import java.util.UUID
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * ジョッキー照会を MCP ツールとして公開する adapter（REST の [com.example.api.controller.jockey.JockeyController] と対になる
 * MCP アダプタ）。application の [GetJockeyUseCase] を再利用し、`Result` を MCP ツール結果へ写す。
 *
 * `@McpTool` を application 層に直付けすると MCP 関心がドメイン側へ漏れる（オニオン違反）ため、変換責務は
 * 必ずこのアダプタが持つ。失敗（不在・不正入力）は例外として送出し、Spring AI が MCP の `isError` ツール 結果へ写す（controller の
 * `orThrowProblem()` と同様、境界での throw は adapter に許される）。
 *
 * REST がパスの `{worldId}` から `Actor` を組むのに対し、ここではツール引数の `worldId` から [McpActorFactory] が組む（#712 /
 * ADR-0067）。
 */
@Profile("local")
@Component
class JockeyMcpTools(
    private val actors: McpActorFactory,
    private val getJockeyUseCase: GetJockeyUseCase,
) {

    @McpTool(name = "get_jockey", description = "世界IDとジョッキーIDで登録済みジョッキーを照会する")
    fun getJockey(worldId: String, jockeyId: String): JockeyMcpResult {
        val actor = actors.actorFor(worldId)
        val id = UUID.fromString(jockeyId)
        return getJockeyUseCase(actor, GetJockeyQuery(id))
            .map { view ->
                JockeyMcpResult(
                    id = view.id.toString(),
                    firstName = view.firstName,
                    lastName = view.lastName,
                )
            }
            .getOrElse { notFound ->
                throw NoSuchElementException("jockey not found: ${notFound.id}")
            }
    }
}
