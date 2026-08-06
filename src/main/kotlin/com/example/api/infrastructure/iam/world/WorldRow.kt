package com.example.api.infrastructure.iam.world

import java.util.UUID
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table

/**
 * `iam.world` テーブルの行に対応する永続化モデル（ADR-0027）。
 *
 * [accountId] は所有者アカウントへの参照（DB では FK ＋ ON DELETE CASCADE）。集約は ID 参照で他集約を 指す規約のため、ここでも生の UUID で持つ。
 */
@Table(schema = "iam", name = "world")
data class WorldRow(
    @Id @Column("id") val id: UUID,
    @Column("account_id") val accountId: UUID,
    @Column("name") val name: String,
    @Version @Column("version") val version: Long? = null,
)
