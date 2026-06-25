package com.example.api.infrastructure.racing.jockey

import com.example.api.application.racing.jockey.JockeyQueries
import com.example.api.application.racing.jockey.JockeyView
import com.example.api.domain.racing.model.jockey.JockeyId
import java.util.UUID
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

/**
 * 読み取りポート [JockeyQueries] の実装（軽量 CQRS（L2）の Query 側。ADR-0031）。
 *
 * 書き込み側の [JdbcJockeyRepository]（集約 [com.example.api.domain.racing.model.jockey.Jockey] を
 * [JockeyRow] 経由で復元する）とは**別経路**として、`jockey` テーブルを [JdbcClient] で直接 SELECT し、 集約を一切組まずに平坦な
 * [JockeyView] へ詰める。Spring Data のリポジトリ（[JockeySpringDataRepository]）も
 * 永続化モデル（[JockeyRow]）も経由しない——同じテーブルを読んでも経路とモデルを分離するのが L2 の要点。
 *
 * 楽観ロックの `version` 列は読み取りでは不要なため SELECT しない（read は整合性境界を持たない）。
 */
@Repository
class JdbcJockeyQueries(private val jdbcClient: JdbcClient) : JockeyQueries {

    override fun findById(id: JockeyId): JockeyView? =
        jdbcClient
            .sql("SELECT id, first_name, last_name FROM jockey WHERE id = :id")
            .param("id", id.value)
            .query { rs, _ ->
                JockeyView(
                    id = rs.getObject("id", UUID::class.java),
                    firstName = rs.getString("first_name"),
                    lastName = rs.getString("last_name"),
                )
            }
            .optional()
            .orElse(null)
}
