package com.example.api.infrastructure.iam.account

import com.example.api.domain.iam.model.account.Account
import com.example.api.domain.iam.model.account.AccountRepository
import com.example.api.domain.iam.model.account.SubjectId
import com.example.api.domain.shared.AccountId
import com.example.api.domain.shared.UpdateConflict
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.stereotype.Repository

/** ドメインポート [AccountRepository] の唯一の実装。Spring Data JDBC で永続化する（ADR-0027 / ADR-0030）。 */
@Repository
class JdbcAccountRepository(private val rows: AccountSpringDataRepository) : AccountRepository {

    override fun findBySubjectId(subjectId: SubjectId): Account? =
        rows.findBySubjectId(subjectId.value)?.toDomain()

    override fun save(account: Account): Result<Account, UpdateConflict> =
        try {
            Ok(rows.save(account.toRow()).toDomain())
        } catch (_: OptimisticLockingFailureException) {
            // version 不一致（並行更新）または行の並行削除。どちらも「読み取り時点から競合した」として扱う。
            Err(UpdateConflict)
        }

    private fun AccountRow.toDomain(): Account =
        Account.reconstitute(AccountId(id), SubjectId(subjectId), version)

    private fun Account.toRow(): AccountRow = AccountRow(id.value, subjectId.value, version)
}
