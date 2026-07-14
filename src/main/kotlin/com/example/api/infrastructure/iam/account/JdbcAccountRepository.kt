package com.example.api.infrastructure.iam.account

import com.example.api.domain.iam.model.account.Account
import com.example.api.domain.iam.model.account.AccountRepository
import com.example.api.domain.iam.model.account.Role
import com.example.api.domain.iam.model.account.SubjectId
import com.example.api.domain.shared.AccountId
import com.example.api.domain.shared.Permission
import java.util.UUID
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

/**
 * [AccountRepository] の Spring Data JDBC 実装。
 *
 * 役割（`account_role`）は集約の子コレクションとして Spring Data 経由で写す一方、権限マスタ （`role_permission`）は集約の外にある定義なので
 * [JdbcClient] の生 SQL で読む。
 */
@Repository
class JdbcAccountRepository(
    private val rows: AccountSpringDataRepository,
    private val jdbcClient: JdbcClient,
) : AccountRepository {

    override fun findBySubjectId(subjectId: SubjectId): Account? =
        rows.findBySubjectId(subjectId.value)?.toDomain()

    override fun findPermissionsOf(roles: Set<Role>): Set<Permission> {
        if (roles.isEmpty()) return emptySet()
        return jdbcClient
            .sql("SELECT permission FROM iam.role_permission WHERE role_name IN (:roleNames)")
            .param("roleNames", roles.map { it.name })
            .query(String::class.java)
            .set()
            // permission 列は NOT NULL（V15）。JdbcClient の型引数が JSpecify の @Nullable 境界を
            // 持つため Kotlin 側では String? 扱いになるが、スキーマ制約により実際に null は来ない。
            .filterNotNull()
            .map { Permission(it) }
            .toSet()
    }

    override fun save(account: Account): Account = rows.save(account.toRow()).toDomain()

    private fun AccountRow.toDomain(): Account =
        Account.reconstitute(
            id = AccountId(id),
            subjectId = SubjectId(subjectId),
            roles = roles.map { it.toRole(id, subjectId) }.toSet(),
            version = version,
        )

    /**
     * `iam.account_role.role_name` を [Role] enum へ変換する。
     *
     * `role` マスタと Kotlin の [Role] enum は [com.example.api.infrastructure.iam.IamMasterDataTest]
     * で一致を表明しているが、それでも drift（enum 未追従のマイグレーション等）で未知のロール名が
     * 紛れ込む可能性は残る。黙って握りつぶして権限ゼロへ畳み込むと「原因不明の権限ゼロアカウント」 という気づきにくい壊れ方をするため、`Role.valueOf`
     * の例外を握りつぶさず、どのアカウント （id / subject）の、どのロール名が未知かをメッセージに残して fail-loud にする。
     */
    private fun AccountRoleRow.toRole(accountId: UUID, subjectId: String): Role =
        try {
            Role.valueOf(roleName)
        } catch (cause: IllegalArgumentException) {
            throw IllegalArgumentException(
                "iam.account_role に Role enum へ変換できないロール名が入っている: " +
                    "accountId=$accountId, subjectId=$subjectId, roleName=$roleName",
                cause,
            )
        }

    private fun Account.toRow(): AccountRow =
        AccountRow(
            id = id.value,
            subjectId = subjectId.value,
            roles = roles.map { AccountRoleRow(it.name) }.toSet(),
            version = version,
        )
}
