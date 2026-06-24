package com.example.api.infrastructure.racing.jockey

import java.util.UUID
import org.springframework.data.repository.CrudRepository

/**
 * [#338 spike] Spring Data JDBC が実装を生成する [JockeyRow] の CRUD リポジトリ（ADR-0025）。
 *
 * これは infrastructure 内部の永続化詳細であり、ドメインポート
 * [com.example.api.domain.racing.model.jockey.JockeyRepository] とは別物。 ドメインポートの実装は本リポジトリを 委譲先に持つアダプタ
 * [JdbcJockeyRepository] が担う。
 */
interface JockeySpringDataRepository : CrudRepository<JockeyRow, UUID> {
    /** 同姓同名（名・姓の完全一致）で検索する。 */
    fun findByFirstNameAndLastName(firstName: String, lastName: String): JockeyRow?
}
