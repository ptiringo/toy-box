package com.example.api.domain.iam.model.account

import com.example.api.domain.shared.AccountId
import com.example.api.domain.shared.generateId

/**
 * テスト用の [Account] Object Mother。
 *
 * 不変条件の検証を経ずに任意のアカウントを組みたい場面で使う（検証そのものは [AccountTest] が担う）。
 */
object AccountFixture {
    fun account(
        id: AccountId = AccountId(generateId()),
        subjectId: String = "test-subject",
        version: Long? = null,
    ): Account = Account.reconstitute(id, SubjectId(subjectId), version)
}
