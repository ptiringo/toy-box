package com.example.api.infrastructure.iam.account

import java.util.UUID
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.MappedCollection
import org.springframework.data.relational.core.mapping.Table

/** `iam.account` の永続化 Row。役割は集約境界の内側なので子コレクションとして写す（ADR-0027）。 */
@Table(schema = "iam", name = "account")
data class AccountRow(
    @Id @Column("id") val id: UUID,
    @Column("subject_id") val subjectId: String,
    @MappedCollection(idColumn = "account_id") val roles: Set<AccountRoleRow>,
    @Version @Column("version") val version: Long? = null,
)

/** `iam.account_role` の永続化 Row（`account` 集約の子）。 */
@Table(schema = "iam", name = "account_role")
data class AccountRoleRow(@Column("role_name") val roleName: String)
