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

    @Test
    fun `未登録の subject なら saveIfAbsent がそのまま保存する`() {
        val saved = repository.saveIfAbsent(newAccount("sub-if-absent-new"))

        assert(repository.findBySubjectId(SubjectId("sub-if-absent-new"))?.id == saved.id)
    }

    @Test
    fun `同じ subject を saveIfAbsent で二重に保存しても増えず先着が返る`() {
        val first = repository.saveIfAbsent(newAccount("sub-if-absent-dup"))
        // ID は Account.create() が採番するため、2 回目は先着とは別の ID を持つ集約を渡している。
        // 返るのが先着の ID なら「insert せず既存を読み直した」ことの証拠になる。
        val second = repository.saveIfAbsent(newAccount("sub-if-absent-dup"))

        assert(second.id == first.id)
        assert(second.subjectId == SubjectId("sub-if-absent-dup"))
        assert(second.version == first.version)
    }

    @Test
    fun `saveIfAbsent の初回保存は save と同じ version を採番する`() {
        // saveIfAbsent は upsert のため Spring Data JDBC を通さず INSERT 文を手書きする。
        // 初期 version が save（Spring Data JDBC 採番）とずれると、以後の楽観ロック更新の
        // 前提が経路によって食い違うため、ここで縛る。
        val bySave =
            repository.save(newAccount("sub-version-save")).getOrThrow {
                AssertionError(it.toString())
            }
        val byIfAbsent = repository.saveIfAbsent(newAccount("sub-version-if-absent"))

        assert(byIfAbsent.version == bySave.version)
    }
}
