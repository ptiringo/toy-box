package com.example.api.infrastructure.racing.jockey

import java.util.UUID
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table

/**
 * [#338 spike] jockey テーブルの行に対応する永続化モデル（ADR-0025）。
 *
 * オニオン規約上、ドメイン集約 [com.example.api.domain.racing.model.jockey.Jockey] は `org.springframework..`
 * へ依存できない（ArchUnit で強制）。そのため Spring Data JDBC のマッピングアノテーションはドメインに付けず、 infrastructure
 * 層の本クラスに閉じ込め、ドメイン集約とは手書きマッパーで相互変換する。
 *
 * - [id] は外部採番の UUIDv7（ドメインの `JockeyId` の生値）。`@Id` を付けるが DB 採番はしない。
 * - [version] は楽観ロック用の `@Version` 列。null のとき Spring Data JDBC は「新規」とみなして insert する （ID が常に非 null
 *   な外部採番でも insert/update を正しく判別できる。ADR-0025 の落とし穴②③）。
 */
@Table("jockey")
data class JockeyRow(
    @Id @Column("id") val id: UUID,
    @Column("first_name") val firstName: String,
    @Column("last_name") val lastName: String,
    @Version @Column("version") val version: Long? = null,
)
