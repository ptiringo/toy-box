package com.example.api.infrastructure.studbook.breeding

import java.time.LocalDate
import java.util.UUID
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table

/**
 * breeding_result テーブルの行に対応する永続化モデル（ADR-0027 / ADR-0030 / #435）。
 *
 * オニオン規約上、ドメイン集約 [com.example.api.domain.studbook.model.breeding.BreedingResult] は
 * `org.springframework..` へ依存できない（ArchUnit で強制）。そのため Spring Data JDBC のマッピングアノテーションは
 * ドメインに付けず本クラスに閉じ込め、ドメイン集約とは手書きマッパーで相互変換する（[JdbcBreedingResultRepository]）。
 *
 * - [id] は外部採番の UUIDv7（ドメインの `BreedingResultId` の生値）。`@Id` を付けるが DB 採番はしない。
 * - [breedingYear] は `java.time.Year` の int 値。
 * - 種付（nullable な `Covering`）は子テーブルを設けず、[coveringStallionId] / [coveringDate] / [coveringPlace] /
 *   [coveringCertificateNumber] にフラット化する。種付した年は coveringStallionId / coveringDate /
 *   coveringCertificateNumber が non-null（coveringPlace は Covering 内でも nullable）、 種付せずの年は 4 列とも
 *   null。
 * - 分娩結果（sealed `FoalingOutcome?`）は判別子 [outcomeType]（enum 名）と、`LiveFoal` のみが持つ分娩日
 *   [outcomeFoalingDate] にフラット化する。種付した年で未報告なら outcomeType は null。
 * - [reportSubmittedOn] は繁殖成績報告書（様式第14号）の提出日（未提出は null）。「提出済みなら分娩結果確定済み」の整合は CHECK
 *   制約でスキーマ側にも強制する（V8 参照）。
 * - covering の有無と区分（NotCovered）・分娩日の整合は CHECK 制約でスキーマ側にも強制する（V4 参照）。
 * - [version] は楽観ロック用の `@Version` 列。null のとき Spring Data JDBC は「新規」とみなして insert する。
 */
@Table(schema = "studbook", name = "breeding_result")
data class BreedingResultRow(
    @Id @Column("id") val id: UUID,
    @Column("breeding_registration_id") val breedingRegistrationId: UUID,
    @Column("breeding_year") val breedingYear: Int,
    @Column("covering_stallion_id") val coveringStallionId: UUID? = null,
    @Column("covering_date") val coveringDate: LocalDate? = null,
    @Column("covering_place") val coveringPlace: String? = null,
    @Column("covering_certificate_number") val coveringCertificateNumber: String? = null,
    @Column("outcome_type") val outcomeType: String? = null,
    @Column("outcome_foaling_date") val outcomeFoalingDate: LocalDate? = null,
    @Column("report_submitted_on") val reportSubmittedOn: LocalDate? = null,
    @Version @Column("version") val version: Long? = null,
)
