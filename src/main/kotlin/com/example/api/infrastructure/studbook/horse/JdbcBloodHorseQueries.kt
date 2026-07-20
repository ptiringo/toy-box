package com.example.api.infrastructure.studbook.horse

import com.example.api.application.studbook.horse.BloodHorseQueries
import com.example.api.application.studbook.horse.BloodHorseView
import com.example.api.domain.studbook.model.horse.bloodhorse.BreedType
import com.example.api.domain.studbook.model.horse.bloodhorse.CoatColor
import com.example.api.domain.studbook.model.horse.bloodhorse.Sex
import java.time.LocalDate
import java.util.UUID
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

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
}
