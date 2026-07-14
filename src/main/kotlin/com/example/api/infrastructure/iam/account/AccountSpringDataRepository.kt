package com.example.api.infrastructure.iam.account

import java.util.UUID
import org.springframework.data.repository.CrudRepository

/**
 * Spring Data JDBC が実装を生成する [AccountRow] の CRUD リポジトリ。
 *
 * これは infrastructure 内部の永続化詳細であり、ドメインポート
 * [com.example.api.domain.iam.model.account.AccountRepository] とは別物。ドメインポートの実装は本リポジトリを 委譲先に持つアダプタ
 * [JdbcAccountRepository] が担う。
 */
interface AccountSpringDataRepository : CrudRepository<AccountRow, UUID> {
    /** IdP の subject（`iam.account.subject_id`）から引き当てる。 */
    fun findBySubjectId(subjectId: String): AccountRow?
}
