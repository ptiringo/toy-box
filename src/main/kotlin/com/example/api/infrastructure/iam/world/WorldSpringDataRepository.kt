package com.example.api.infrastructure.iam.world

import java.util.UUID
import org.springframework.data.repository.CrudRepository

/** Spring Data JDBC が実装を生成する [WorldRow] の CRUD リポジトリ（ADR-0027）。 */
interface WorldSpringDataRepository : CrudRepository<WorldRow, UUID> {
    /** そのアカウントが世界を 1 つでも持っているかを判定する。 */
    fun existsByAccountId(accountId: UUID): Boolean
}
