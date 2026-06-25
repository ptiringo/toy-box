package com.example.api.infrastructure.studbook.horse

import java.time.LocalDate
import java.util.UUID
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table

/**
 * blood_horse テーブルの行に対応する永続化モデル（ADR-0027 / ADR-0030 / #435）。
 *
 * オニオン規約上、ドメイン集約 [com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorse] は
 * `org.springframework..` へ依存できない（ArchUnit で強制）。そのため Spring Data JDBC のマッピングアノテーションは
 * ドメインに付けず本クラスに閉じ込め、ドメイン集約とは手書きマッパーで相互変換する（[JdbcBloodHorseRepository]）。
 *
 * - [id] は外部採番の UUIDv7（ドメインの `BloodHorseId` の生値）。`@Id` を付けるが DB 採番はしない。
 * - 各種 enum（性・毛色・品種）は enum 名を文字列で持つ。
 * - 出自（sealed `Origin`）は子テーブルを設けず、判別子 [originType]（`DOMESTIC` / `IMPORTED`）と各バリアントの
 *   属性列をフラットに並べて表す。内国産なら [sireId] / [damId] が non-null、輸入なら [originCountry] / [landingDate] が
 *   non-null（相互排他は判別子で復元する）。retirement と同様の nullable フラット化方針。
 * - [version] は楽観ロック用の `@Version` 列。null のとき Spring Data JDBC は「新規」とみなして insert する （外部採番で `@Id`
 *   が常に非 null でも insert/update を正しく判別できる。ADR-0027 の落とし穴②③）。
 */
@Table("blood_horse")
data class BloodHorseRow(
    @Id @Column("id") val id: UUID,
    @Column("registration_number") val registrationNumber: String,
    @Column("sex") val sex: String,
    @Column("coat_color") val coatColor: String,
    @Column("breed_type") val breedType: String,
    @Column("date_of_birth") val dateOfBirth: LocalDate,
    @Column("breeder") val breeder: String,
    @Column("microchip_number") val microchipNumber: String,
    @Column("name") val name: String? = null,
    @Column("origin_type") val originType: String,
    @Column("sire_id") val sireId: UUID? = null,
    @Column("dam_id") val damId: UUID? = null,
    @Column("origin_country") val originCountry: String? = null,
    @Column("landing_date") val landingDate: LocalDate? = null,
    @Version @Column("version") val version: Long? = null,
)
