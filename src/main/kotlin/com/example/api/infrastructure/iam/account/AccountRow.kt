package com.example.api.infrastructure.iam.account

import java.util.UUID
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table

/**
 * `iam.account` テーブルの行に対応する永続化モデル（ADR-0027）。
 *
 * ドメイン集約 [com.example.api.domain.iam.model.account.Account] は `org.springframework..` へ依存できない
 * （ArchUnit で強制）。そのため Spring Data JDBC のマッピングは本クラスに閉じ込め、手書きマッパーで相互変換する。
 */
@Table(schema = "iam", name = "account")
data class AccountRow(
    @Id @Column("id") val id: UUID,
    @Column("subject_id") val subjectId: String,
    @Version @Column("version") val version: Long? = null,
)
