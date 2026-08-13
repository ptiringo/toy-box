package com.example.api.infrastructure.studbook.breeding

import com.example.api.application.studbook.breeding.CoveringReportDetailView
import com.example.api.application.studbook.breeding.CoveringReportQueries
import com.example.api.domain.shared.WorldId
import com.example.api.domain.studbook.model.breeding.CoveringReportId
import java.time.LocalDate
import java.util.UUID
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

/**
 * 読み取りポート [CoveringReportQueries] の実装（軽量 CQRS / L2 の Query 側。ADR-0031）。
 *
 * 書き込み側の [JdbcCoveringReportRepository]（集約を `CoveringReportRow` 経由で復元する）とは別経路として、
 * `studbook.covering_report` を [JdbcClient] で直接 SELECT し、集約を組まずに平坦な [CoveringReportDetailView]
 * へ詰める。
 */
@Repository
class JdbcCoveringReportQueries(private val jdbcClient: JdbcClient) : CoveringReportQueries {

    override fun findById(worldId: WorldId, id: CoveringReportId): CoveringReportDetailView? =
        jdbcClient
            .sql(
                """
                SELECT
                    id, stallion_breeding_registration_id, covering_year, submitted_on
                FROM studbook.covering_report
                WHERE id = :id AND world_id = :worldId
                """
                    .trimIndent()
            )
            .param("id", id.value)
            .param("worldId", worldId.value)
            .query { rs, _ ->
                CoveringReportDetailView(
                    id = rs.getObject("id", UUID::class.java),
                    stallionBreedingRegistrationId =
                        rs.getObject("stallion_breeding_registration_id", UUID::class.java),
                    coveringYear = rs.getInt("covering_year"),
                    submittedOn = rs.getObject("submitted_on", LocalDate::class.java),
                )
            }
            .optional()
            .orElse(null)
}
