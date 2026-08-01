package com.example.api.infrastructure.studbook.horse

import com.example.api.application.studbook.horse.BloodHorseDetailView
import com.example.api.application.studbook.horse.BloodHorseQueries
import com.example.api.application.studbook.horse.BloodHorseView
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseId
import com.example.api.domain.studbook.model.horse.bloodhorse.BreedType
import com.example.api.domain.studbook.model.horse.bloodhorse.CoatColor
import com.example.api.domain.studbook.model.horse.bloodhorse.LandingDate
import com.example.api.domain.studbook.model.horse.bloodhorse.Origin
import com.example.api.domain.studbook.model.horse.bloodhorse.OriginCountry
import com.example.api.domain.studbook.model.horse.bloodhorse.Sex
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getOrThrow
import java.sql.ResultSet
import java.time.LocalDate
import java.util.UUID
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

/**
 * 検証済みで保存された VO 値を復元時に取り出すヘルパー。`create` が `Result` を返す VO を、DB 由来の trusted データとして失敗しない前提で取り出す（Err
 * は復元データ破損を示す `IllegalStateException`）。[JdbcBloodHorseRepository] のファイルスコープ private
 * 拡張と同じ内容だが、Kotlin のトップレベル `private` はファイル外から import できないため個別に持つ。
 */
private fun <V, E> Result<V, E>.orThrow(): V = getOrThrow {
    IllegalStateException("永続化された値の復元に失敗しました: $it")
}

/**
 * 読み取りポート [BloodHorseQueries] の実装（軽量 CQRS / L2 の Query 側。ADR-0031）。
 *
 * 書き込み側の `JdbcBloodHorseRepository`（集約 `BloodHorse` を `BloodHorseRow` 経由で復元する）とは別経路として、
 * `studbook.blood_horse` を [JdbcClient] で直接 SELECT し、集約を組まずに平坦な [BloodHorseView] へ詰める。 enum
 * 列（性・毛色・品種）は enum 名文字列を `valueOf` でドメイン enum へ戻す。
 */
@Repository
class JdbcBloodHorseQueries(private val jdbcClient: JdbcClient) : BloodHorseQueries {

    override fun findAll(): List<BloodHorseView> =
        jdbcClient
            .sql(
                """
                SELECT
                    id, registration_number, sex, coat_color, breed_type,
                    date_of_birth, breeder, name
                FROM studbook.blood_horse
                ORDER BY id
                """
                    .trimIndent()
            )
            .query { rs, _ ->
                BloodHorseView(
                    id = rs.getObject("id", UUID::class.java),
                    registrationNumber = rs.getString("registration_number"),
                    sex = Sex.valueOf(rs.getString("sex")),
                    coatColor = CoatColor.valueOf(rs.getString("coat_color")),
                    breedType = BreedType.valueOf(rs.getString("breed_type")),
                    dateOfBirth = rs.getObject("date_of_birth", LocalDate::class.java),
                    breeder = rs.getString("breeder"),
                    name = rs.getString("name"),
                )
            }
            .list()

    override fun findById(id: BloodHorseId): BloodHorseDetailView? =
        jdbcClient
            .sql(
                """
                SELECT
                    bh.id, bh.registration_number, bh.sex, bh.coat_color, bh.breed_type,
                    bh.date_of_birth, bh.breeder, bh.name,
                    bh.origin_type, bh.sire_id, bh.dam_id, bh.origin_country, bh.landing_date,
                    hi.microchip_number
                FROM studbook.blood_horse bh
                JOIN studbook.horse_inspection hi ON hi.id = bh.inspection_id
                WHERE bh.id = :id
                """
                    .trimIndent()
            )
            .param("id", id.value)
            .query { rs, _ ->
                BloodHorseDetailView(
                    id = rs.getObject("id", UUID::class.java),
                    registrationNumber = rs.getString("registration_number"),
                    sex = Sex.valueOf(rs.getString("sex")),
                    coatColor = CoatColor.valueOf(rs.getString("coat_color")),
                    breedType = BreedType.valueOf(rs.getString("breed_type")),
                    dateOfBirth = rs.getObject("date_of_birth", LocalDate::class.java),
                    breeder = rs.getString("breeder"),
                    microchipNumber = rs.getString("microchip_number"),
                    origin = rs.toOrigin(),
                    name = rs.getString("name"),
                )
            }
            .optional()
            .orElse(null)

    /**
     * 判別子 `origin_type` と各バリアント列から sealed [Origin] を復元する。
     *
     * 列は判別子に応じて一方のバリアントにしか現れない（CHECK 制約 `chk_blood_horse_origin` がスキーマ側でも 強制している）。判別子と実データの不整合は DB
     * の破損であり業務エラーではないため、infrastructure 層の 例外として送出する（error-handling.md）。
     */
    private fun ResultSet.toOrigin(): Origin =
        when (val originType = getString("origin_type")) {
            "DOMESTIC" ->
                Origin.Domestic(
                    sireId = BloodHorseId(getObject("sire_id", UUID::class.java)),
                    damId = BloodHorseId(getObject("dam_id", UUID::class.java)),
                )
            "IMPORTED" ->
                Origin.Imported(
                    originCountry = OriginCountry.create(getString("origin_country")).orThrow(),
                    landingDate = LandingDate(getObject("landing_date", LocalDate::class.java)),
                )
            "CARRIED_OVER" -> Origin.CarriedOver
            else -> error("未知の origin_type です: $originType")
        }
}
