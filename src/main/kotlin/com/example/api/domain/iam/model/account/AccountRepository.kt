package com.example.api.domain.iam.model.account

import com.example.api.domain.shared.UpdateConflict
import com.github.michaelbull.result.Result
import org.jmolecules.ddd.annotation.Repository

/**
 * [Account] の永続化を担うポート。
 *
 * ドメイン層はこのインターフェースのみを参照する。実装は infrastructure 層に置く。
 */
@Repository
interface AccountRepository {
    /** IdP の subject でアカウントを引く。存在しなければ null（単純 lookup は Result を強制しない）。 */
    fun findBySubjectId(subjectId: SubjectId): Account?

    /**
     * アカウントを永続化する。
     *
     * 集約の [Account.version] が null なら insert、非 null なら楽観ロック付き update になる。
     */
    fun save(account: Account): Result<Account, UpdateConflict>
}
