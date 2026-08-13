package com.example.api.infrastructure.studbook.breeding

import com.example.api.application.studbook.breeding.BreedingResultDetailView
import com.example.api.application.studbook.breeding.BreedingResultQueries
import com.example.api.domain.shared.WorldId
import com.example.api.domain.studbook.model.breeding.BreedingResultId
import com.example.api.domain.studbook.model.breeding.FoalingOutcome
import java.sql.ResultSet
import java.time.LocalDate
import java.util.UUID
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

/**
 * 読み取りポート [BreedingResultQueries] の実装（軽量 CQRS / L2 の Query 側。ADR-0031）。
 *
 * 書き込み側の [JdbcBreedingResultRepository]（集約を `BreedingResultRow` 経由で復元する）とは別経路として、
 * `studbook.breeding_result` を [JdbcClient] で直接 SELECT し、集約を組まずに平坦な [BreedingResultDetailView]
 * へ詰める。種付は列をそのまま平置きし（種付せずの年は 4 列とも NULL）、分娩結果は判別子 `outcome_type` から sealed [FoalingOutcome]
 * を復元する。提出の期限超過は列を持たず View が導出する。
 */
@Repository
class JdbcBreedingResultQueries(private val jdbcClient: JdbcClient) : BreedingResultQueries {

    override fun findById(worldId: WorldId, id: BreedingResultId): BreedingResultDetailView? =
        jdbcClient
            .sql(
                """
                SELECT
                    id, breeding_registration_id, breeding_year,
                    covering_stallion_id, covering_date, covering_place,
                    covering_certificate_number,
                    outcome_type, outcome_foaling_date, report_submitted_on
                FROM studbook.breeding_result
                WHERE id = :id AND world_id = :worldId
                """
                    .trimIndent()
            )
            .param("id", id.value)
            .param("worldId", worldId.value)
            .query { rs, _ ->
                BreedingResultDetailView(
                    id = rs.getObject("id", UUID::class.java),
                    breedingRegistrationId =
                        rs.getObject("breeding_registration_id", UUID::class.java),
                    breedingYear = rs.getInt("breeding_year"),
                    stallionId = rs.getObject("covering_stallion_id", UUID::class.java),
                    coveringDate = rs.getObject("covering_date", LocalDate::class.java),
                    coveringPlace = rs.getString("covering_place"),
                    certificateNumber = rs.getString("covering_certificate_number"),
                    outcome = rs.toOutcome(),
                    reportSubmittedOn = rs.getObject("report_submitted_on", LocalDate::class.java),
                )
            }
            .optional()
            .orElse(null)

    /**
     * 判別子 `outcome_type` から sealed [FoalingOutcome] を復元する（未報告なら NULL のまま null）。
     *
     * 分娩日を伴うのは生産（`LIVE_FOAL`）だけで、その整合は CHECK 制約がスキーマ側でも強制している（V4）。
     * 制約をすり抜けた壊れ行に当たったときにどの成績かを残すため、書き込み側（[JdbcBreedingResultRepository]）と 対称に診断メッセージつきの
     * `checkNotNull` で受ける。
     */
    private fun ResultSet.toOutcome(): FoalingOutcome? {
        // 列は先に取り出す。checkNotNull の呼び出しごと 1 行に収め、診断メッセージのラムダが単独行に
        // 折られない形にしている（壊れ行でしか通らない行を独立させると常に未カバーになるため）。
        val id = getObject("id", UUID::class.java)
        val foalingDate = getObject("outcome_foaling_date", LocalDate::class.java)
        return when (val outcomeType = getString("outcome_type")) {
            null -> null
            OutcomeType.LIVE_FOAL ->
                FoalingOutcome.LiveFoal(checkNotNull(foalingDate) { "生産の分娩日が欠落: id=$id" })
            OutcomeType.NOT_CONCEIVED -> FoalingOutcome.NotConceived
            OutcomeType.ABORTION -> FoalingOutcome.Abortion
            OutcomeType.TWIN_ABORTION -> FoalingOutcome.TwinAbortion
            OutcomeType.STILLBIRTH -> FoalingOutcome.Stillbirth
            OutcomeType.TWIN_STILLBIRTH -> FoalingOutcome.TwinStillbirth
            OutcomeType.NEONATAL_DEATH -> FoalingOutcome.NeonatalDeath
            OutcomeType.TWIN_NEONATAL_DEATH -> FoalingOutcome.TwinNeonatalDeath
            OutcomeType.NOT_COVERED -> FoalingOutcome.NotCovered
            else -> error("未知の outcome_type です: $outcomeType (id=$id)")
        }
    }
}
