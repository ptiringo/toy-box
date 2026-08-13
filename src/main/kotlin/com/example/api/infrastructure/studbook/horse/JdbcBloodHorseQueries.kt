package com.example.api.infrastructure.studbook.horse

import com.example.api.application.studbook.horse.BloodHorseDetailView
import com.example.api.application.studbook.horse.BloodHorseQueries
import com.example.api.application.studbook.horse.BloodHorseView
import com.example.api.domain.shared.WorldId
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseId
import com.example.api.domain.studbook.model.horse.bloodhorse.BreedType
import com.example.api.domain.studbook.model.horse.bloodhorse.CoatColor
import com.example.api.domain.studbook.model.horse.bloodhorse.LandingDate
import com.example.api.domain.studbook.model.horse.bloodhorse.Origin
import com.example.api.domain.studbook.model.horse.bloodhorse.OriginCountry
import com.example.api.domain.studbook.model.horse.bloodhorse.Sex
import com.example.api.infrastructure.shared.orThrow
import java.sql.ResultSet
import java.time.LocalDate
import java.util.UUID
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

/**
 * 読み取りポート [BloodHorseQueries] の実装（軽量 CQRS / L2 の Query 側。ADR-0031）。
 *
 * 書き込み側の `JdbcBloodHorseRepository`（集約 `BloodHorse` を `BloodHorseRow` 経由で復元する）とは別経路として、
 * `studbook.blood_horse` を [JdbcClient] で直接 SELECT し、集約を組まずに平坦な [BloodHorseView] へ詰める。 enum
 * 列（性・毛色・品種）は enum 名文字列を `valueOf` でドメイン enum へ戻す。一覧 [findAll] は `blood_horse` 単独から
 * [BloodHorseView] を組むのに対し、単体取得 [findById] は `studbook.horse_inspection` と JOIN して
 * マイクロチップ番号も含めた完全表現 [BloodHorseDetailView] を組む。
 */
@Repository
class JdbcBloodHorseQueries(private val jdbcClient: JdbcClient) : BloodHorseQueries {

    override fun findAll(worldId: WorldId): List<BloodHorseView> =
        jdbcClient
            .sql(
                """
                SELECT
                    id, registration_number, sex, coat_color, breed_type,
                    date_of_birth, breeder, name
                FROM studbook.blood_horse
                WHERE world_id = :worldId
                ORDER BY id
                """
                    .trimIndent()
            )
            .param("worldId", worldId.value)
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

    override fun findById(worldId: WorldId, id: BloodHorseId): BloodHorseDetailView? =
        jdbcClient
            .sql(
                """
                SELECT
                    bh.id, bh.registration_number, bh.sex, bh.coat_color, bh.breed_type,
                    bh.date_of_birth, bh.breeder, bh.name,
                    bh.origin_type, bh.sire_id, bh.dam_id, bh.origin_country, bh.landing_date,
                    hi.microchip_number
                FROM studbook.blood_horse bh
                JOIN studbook.horse_inspection hi
                    ON hi.id = bh.inspection_id AND hi.world_id = bh.world_id
                WHERE bh.id = :id AND bh.world_id = :worldId
                """
                    .trimIndent()
            )
            .param("id", id.value)
            .param("worldId", worldId.value)
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
     * の破損であり業務エラーではないため、infrastructure 層の 例外として送出する（error-handling.md）。CHECK
     * 制約をすり抜けた壊れ行に当たったときに「どの馬のどの列が 欠落か」を残すため、バリアント固有列は書き込み側（[JdbcBloodHorseRepository]）と対称に
     * 診断メッセージつきの `checkNotNull` で受ける（素の NPE にしない）。
     */
    private fun ResultSet.toOrigin(): Origin {
        val id = getObject("id", UUID::class.java)
        return when (val originType = getString("origin_type")) {
            OriginType.DOMESTIC -> {
                // 列は先に取り出す。checkNotNull の呼び出しごと 1 行に収め、診断メッセージのラムダが
                // 単独行に折られない形にしている（壊れ行でしか通らない行を独立させると常に未カバーになるため）。
                val sire = getObject("sire_id", UUID::class.java)
                val dam = getObject("dam_id", UUID::class.java)
                Origin.Domestic(
                    sireId = BloodHorseId(checkNotNull(sire) { "内国産の父IDが欠落: id=$id" }),
                    damId = BloodHorseId(checkNotNull(dam) { "内国産の母IDが欠落: id=$id" }),
                )
            }
            OriginType.IMPORTED -> {
                val country = getString("origin_country")
                val landing = getObject("landing_date", LocalDate::class.java)
                Origin.Imported(
                    originCountry =
                        OriginCountry.create(checkNotNull(country) { "輸入の原産国が欠落: id=$id" })
                            .orThrow(),
                    landingDate = LandingDate(checkNotNull(landing) { "輸入の揚陸日が欠落: id=$id" }),
                )
            }
            OriginType.CARRIED_OVER -> Origin.CarriedOver
            else -> error("未知の origin_type です: $originType (id=$id)")
        }
    }
}
