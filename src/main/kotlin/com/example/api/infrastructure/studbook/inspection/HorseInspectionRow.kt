package com.example.api.infrastructure.studbook.inspection

import java.util.UUID
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table

/**
 * horse_inspection テーブルの行に対応する永続化モデル（#312。ADR-0027 / ADR-0030 / #435）。
 *
 * ドメイン集約 [com.example.api.domain.studbook.model.inspection.HorseInspection] は
 * `org.springframework..` へ依存できない（ArchUnit）。Spring Data JDBC のマッピングアノテーションは本クラスに
 * 閉じ込め、ドメインとは手書きマッパー（[JdbcHorseInspectionRepository]）で相互変換する。
 *
 * - [id] は外部採番の UUIDv7（ドメインの `HorseInspectionId` の生値）。`@Id` を付けるが DB 採番はしない。
 * - 親子判定（sealed `ParentageDetermination`）は判別子 [parentageType] と、`ByDna` のみ持つ [dnaParentageResult] に
 *   フラット化する。特徴記述子（nullable `IdentificationFeatures`）は feature_* 列に nullable でフラット化する。
 * - [version] は楽観ロック用の `@Version` 列。null のとき Spring Data JDBC は「新規」とみなして insert する。
 */
@Table(schema = "studbook", name = "horse_inspection")
data class HorseInspectionRow(
    @Id @Column("id") val id: UUID,
    @Column("microchip_number") val microchipNumber: String,
    @Column("parentage_type") val parentageType: String,
    @Column("dna_parentage_result") val dnaParentageResult: String? = null,
    @Column("feature_hair_whorl") val featureHairWhorl: String? = null,
    @Column("feature_white_markings") val featureWhiteMarkings: String? = null,
    @Column("feature_nose_print") val featureNosePrint: String? = null,
    @Version @Column("version") val version: Long? = null,
)
