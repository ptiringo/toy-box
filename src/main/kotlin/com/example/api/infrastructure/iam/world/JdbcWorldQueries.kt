package com.example.api.infrastructure.iam.world

import com.example.api.application.iam.world.WorldQueries
import com.example.api.application.iam.world.WorldView
import com.example.api.domain.shared.AccountId
import java.util.UUID
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

/** 読み取りポート [WorldQueries] の実装。集約を経由せず `iam.world` を直接読む（ADR-0031）。 */
@Repository
class JdbcWorldQueries(private val jdbcClient: JdbcClient) : WorldQueries {

    override fun findAllByAccountId(accountId: AccountId): List<WorldView> =
        jdbcClient
            .sql("SELECT id, name FROM iam.world WHERE account_id = :accountId ORDER BY id")
            .param("accountId", accountId.value)
            .query { rs, _ ->
                WorldView(id = rs.getObject("id", UUID::class.java), name = rs.getString("name"))
            }
            .list()
}
