package com.example.api.infrastructure.iam.account

import com.example.api.domain.iam.model.account.Account
import com.example.api.domain.iam.model.account.AccountRepository
import com.example.api.domain.iam.model.account.Role
import com.example.api.domain.iam.model.account.SubjectId
import com.example.api.domain.shared.Permission
import com.example.api.domain.shared.generateId
import com.example.api.domain.studbook.model.StudbookPermissions
import com.example.api.support.PostgresContainerSupport
import com.example.api.support.deleteAllIamTables
import com.github.michaelbull.result.unwrap
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.context.TestConstructor
import org.springframework.test.context.TestConstructor.AutowireMode

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestConstructor(autowireMode = AutowireMode.ALL)
class JdbcAccountRepositoryTest(
    private val repository: AccountRepository,
    private val jdbcClient: JdbcClient,
) : PostgresContainerSupport() {

    @BeforeEach
    fun cleanUp() {
        deleteAllIamTables(jdbcClient)
    }

    @Nested
    inner class SuccessCase {
        @Test
        fun `保存したアカウントを subject で引き当てられ役割も復元される`() {
            val account =
                Account.create(SubjectId("idp-sub-001"), setOf(Role.REGISTRAR, Role.BREEDER))
                    .unwrap()
            repository.save(account)

            val found = repository.findBySubjectId(SubjectId("idp-sub-001"))

            assert(found != null)
            assert(found!!.id == account.id)
            assert(found.roles == setOf(Role.REGISTRAR, Role.BREEDER))
        }

        @Test
        fun `役割に紐づく権限をマスタから展開できる`() {
            val permissions = repository.findPermissionsOf(setOf(Role.BREEDER))

            assert(StudbookPermissions.BREEDING_RESULT_REPORT_FOALING in permissions)
            assert(StudbookPermissions.HORSE_REGISTER !in permissions)
        }

        @Test
        fun `複数の役割を持つとき権限は和集合になる`() {
            val permissions = repository.findPermissionsOf(setOf(Role.BREEDER, Role.REGISTRAR))

            assert(StudbookPermissions.HORSE_REGISTER in permissions)
            assert(StudbookPermissions.BREEDING_RESULT_REPORT_FOALING in permissions)
        }

        @Test
        fun `権限を持たない役割だけのとき権限は空になる`() {
            val permissions: Set<Permission> = repository.findPermissionsOf(setOf(Role.VIEWER))

            assert(permissions.isEmpty())
        }
    }

    @Nested
    inner class FailureCase {
        @Test
        fun `未登録の subject を引き当てると null を返す`() {
            val found = repository.findBySubjectId(SubjectId("unknown-sub"))

            assert(found == null)
        }

        @Test
        fun `account_roleに未知のロール名が入っていると意味のある例外で落ちる`() {
            // Role enum に無いロール名を意図的に仕込む。FK（fk_account_role_role）を満たすため、
            // 一時的に iam.role へも同名を追加してから account_role に紐づける（テスト後に必ず後始末する）。
            val accountId = generateId()
            val subjectId = "idp-sub-unknown-role"
            val unknownRoleName = "GHOST_ROLE"

            jdbcClient
                .sql("INSERT INTO iam.role (name) VALUES (:name)")
                .param("name", unknownRoleName)
                .update()
            jdbcClient
                .sql(
                    "INSERT INTO iam.account (id, subject_id, version) VALUES (:id, :subjectId, 0)"
                )
                .param("id", accountId)
                .param("subjectId", subjectId)
                .update()
            jdbcClient
                .sql(
                    "INSERT INTO iam.account_role (account_id, role_name) " +
                        "VALUES (:accountId, :roleName)"
                )
                .param("accountId", accountId)
                .param("roleName", unknownRoleName)
                .update()

            try {
                val exception =
                    assertThrows(IllegalArgumentException::class.java) {
                        repository.findBySubjectId(SubjectId(subjectId))
                    }

                // 「何が起きたか」＝どのアカウントの、どのロール名が未知かがメッセージから追える。
                assert(exception.message?.contains(unknownRoleName) == true)
                assert(exception.message?.contains(subjectId) == true)
            } finally {
                // 後始末: account_role → account → role の順で消し、iam.role マスタを汚さない
                // （消し忘れると IamMasterDataTest が以降のテストで偽陽性/偽陰性になる）。
                jdbcClient
                    .sql("DELETE FROM iam.account_role WHERE role_name = :name")
                    .param("name", unknownRoleName)
                    .update()
                jdbcClient
                    .sql("DELETE FROM iam.account WHERE id = :id")
                    .param("id", accountId)
                    .update()
                jdbcClient
                    .sql("DELETE FROM iam.role WHERE name = :name")
                    .param("name", unknownRoleName)
                    .update()
            }
        }
    }
}
