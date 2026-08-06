package com.example.api.infrastructure.iam.account

import com.example.api.domain.iam.model.account.Account
import com.example.api.domain.iam.model.account.AccountRepository
import com.example.api.domain.iam.model.account.SubjectId
import com.example.api.support.PostgresContainerSupport
import com.github.michaelbull.result.getOrThrow
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/** [JdbcAccountRepository] がドメインポート [AccountRepository] の契約を満たすことを実 DB で検証する。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class JdbcAccountRepositoryContractTest : PostgresContainerSupport() {

    @Autowired private lateinit var repository: AccountRepository

    private fun newAccount(subjectId: String): Account =
        Account.create(subjectId).getOrThrow { AssertionError(it.toString()) }

    @Test
    fun `保存したアカウントを subject で引き当てられる`() {
        val saved =
            repository.save(newAccount("sub-roundtrip")).getOrThrow {
                AssertionError(it.toString())
            }

        val found = repository.findBySubjectId(SubjectId("sub-roundtrip"))

        assert(found != null)
        assert(found?.id == saved.id)
        assert(found?.subjectId == SubjectId("sub-roundtrip"))
    }

    @Test
    fun `未登録の subject を引くと null`() {
        val found = repository.findBySubjectId(SubjectId("sub-unknown"))

        assert(found == null)
    }

    @Test
    fun `保存すると version が採番される`() {
        val saved =
            repository.save(newAccount("sub-version")).getOrThrow { AssertionError(it.toString()) }

        assert(saved.version != null)
    }
}
