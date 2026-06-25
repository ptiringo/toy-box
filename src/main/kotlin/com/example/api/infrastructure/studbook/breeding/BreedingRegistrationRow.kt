package com.example.api.infrastructure.studbook.breeding

import java.time.LocalDate
import java.util.UUID
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table

/**
 * breeding_registration テーブルの行に対応する永続化モデル（ADR-0027 / ADR-0030）。
 *
 * オニオン規約上、ドメイン集約 [com.example.api.domain.studbook.model.breeding.BreedingRegistration] は
 * `org.springframework..` へ依存できない（ArchUnit で強制）。そのため Spring Data JDBC のマッピングアノテーションは
 * ドメインに付けず本クラスに閉じ込め、ドメイン集約とは手書きマッパーで相互変換する（[JdbcBreedingRegistrationRepository]）。
 *
 * - [id] は外部採番の UUIDv7（ドメインの `BreedingRegistrationId` の生値）。`@Id` を付けるが DB 採番はしない。
 * - [role] は [com.example.api.domain.studbook.model.breeding.BreedingRole] の enum 名を文字列で持つ。
 * - 供用停止（`BreedingRetirement`）は nullable な値オブジェクトのため、[retirementReason]（enum 名）と
 *   [retirementOccurredOn] の 2 列にフラット化する。両方とも null なら供用中、両方 non-null なら供用停止済み。
 * - [version] は楽観ロック用の `@Version` 列。null のとき Spring Data JDBC は「新規」とみなして insert する （外部採番で `@Id`
 *   が常に非 null でも insert/update を正しく判別できる。ADR-0027 の落とし穴②③）。
 */
@Table("breeding_registration")
data class BreedingRegistrationRow(
    @Id @Column("id") val id: UUID,
    @Column("registration_number") val registrationNumber: String,
    @Column("registered_horse_id") val registeredHorseId: UUID,
    @Column("role") val role: String,
    @Column("retirement_reason") val retirementReason: String? = null,
    @Column("retirement_occurred_on") val retirementOccurredOn: LocalDate? = null,
    @Version @Column("version") val version: Long? = null,
)
