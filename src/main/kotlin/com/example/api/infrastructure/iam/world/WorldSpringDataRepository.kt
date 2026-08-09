package com.example.api.infrastructure.iam.world

import java.util.UUID
import org.springframework.data.repository.CrudRepository

/** Spring Data JDBC が実装を生成する [WorldRow] の CRUD リポジトリ（ADR-0027）。 */
interface WorldSpringDataRepository : CrudRepository<WorldRow, UUID> {
    /** ID とアカウントIDの両方で絞り込む（所有付き lookup の実体）。 */
    fun findByIdAndAccountId(id: UUID, accountId: UUID): WorldRow?

    /** そのアカウントが世界を 1 つでも持っているかを判定する。 */
    fun existsByAccountId(accountId: UUID): Boolean

    /** 同一アカウント内に同名の世界が既にあるかを判定する。 */
    fun existsByAccountIdAndName(accountId: UUID, name: String): Boolean

    /** 同一アカウント内の同名の世界を引く（UNIQUE (account_id, name) により高々 1 行）。 */
    fun findByAccountIdAndName(accountId: UUID, name: String): WorldRow?
}
