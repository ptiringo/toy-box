package com.example.api.infrastructure.studbook.breeding

import java.time.LocalDate
import java.util.UUID
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table

/**
 * covering_report テーブルの行に対応する永続化モデル（#540。ADR-0027 の Row 分離）。
 *
 * オニオン規約上、ドメイン集約 [com.example.api.domain.studbook.model.breeding.CoveringReport] は
 * `org.springframework..` へ依存できない（ArchUnit で強制）。そのため Spring Data JDBC のマッピング
 * アノテーションはドメインに付けず本クラスに閉じ込め、ドメイン集約とは手書きマッパーで相互変換する （[JdbcCoveringReportRepository]）。
 *
 * - [id] は外部採番の UUIDv7（ドメインの `CoveringReportId` の生値）。`@Id` を付けるが DB 採番はしない。
 * - [worldId] はこの行が属する世界（セーブデータ）のID。集約は世界を知らないため、マッパーが引数で受け取って書く。
 * - [coveringYear] は `java.time.Year` の int 値。
 * - 「種牡馬×種付年」の一意性は UNIQUE 制約でスキーマ側にも強制する（V10 参照）。
 * - [version] は楽観ロック用の `@Version` 列。null のとき Spring Data JDBC は「新規」とみなして insert する。
 */
@Table(schema = "studbook", name = "covering_report")
data class CoveringReportRow(
    @Id @Column("id") val id: UUID,
    @Column("world_id") val worldId: UUID,
    @Column("stallion_breeding_registration_id") val stallionBreedingRegistrationId: UUID,
    @Column("covering_year") val coveringYear: Int,
    @Column("submitted_on") val submittedOn: LocalDate,
    @Version @Column("version") val version: Long? = null,
)
