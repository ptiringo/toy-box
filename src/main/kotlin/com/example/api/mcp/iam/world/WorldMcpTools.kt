package com.example.api.mcp.iam.world

import com.example.api.application.iam.world.ListWorldsQuery
import com.example.api.application.iam.world.ListWorldsUseCase
import com.example.api.mcp.McpActorFactory
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * 自分の世界（セーブデータ）の一覧を MCP ツールとして公開する adapter（#712）。
 *
 * 他のツールが要求する `worldId` を調べるための入口であり、これがあるおかげで設定に世界の UUID を書かずに済む。 `iam` のユースケースは `Actor`
 * を取らない側（まだ世界に入っていないブートストラップ）なので、 [McpActorFactory.resolveAccountId] だけで足りる。
 */
@Profile("local")
@Component
class WorldMcpTools(
    private val actors: McpActorFactory,
    private val listWorldsUseCase: ListWorldsUseCase,
) {

    @McpTool(name = "list_worlds", description = "自分のアカウントが持つ世界（セーブデータ）を一覧する")
    fun listWorlds(): List<WorldMcpResult> =
        listWorldsUseCase(ListWorldsQuery(actors.resolveAccountId())).map {
            WorldMcpResult(id = it.id.toString(), name = it.name)
        }
}
