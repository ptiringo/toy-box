package com.example.api.infrastructure.iam.account

import java.util.UUID
import org.springframework.data.repository.CrudRepository

/**
 * Spring Data JDBC が実装を生成する [AccountRow] の CRUD リポジトリ（ADR-0027）。
 *
 * infrastructure 内部の永続化詳細であり、ドメインポート [com.example.api.domain.iam.model.account.AccountRepository]
 * とは別物。
 */
interface AccountSpringDataRepository : CrudRepository<AccountRow, UUID> {
    /** IdP の subject で検索する。 */
    fun findBySubjectId(subjectId: String): AccountRow?
}
