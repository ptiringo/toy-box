package com.example.api.infrastructure.studbook.breeding

import com.example.api.application.studbook.breeding.BreedingResultSummaryQueries
import com.example.api.application.studbook.breeding.BreedingResultSummaryView
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseId
import java.util.UUID
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

/**
 * 読み取りポート [BreedingResultSummaryQueries] の実装（軽量 CQRS / L2 の Query 側。ADR-0031）。
 *
 * 書き込み側の [JdbcBreedingResultRepository]（集約
 * [com.example.api.domain.studbook.model.breeding.BreedingResult] を [BreedingResultRow] 経由で復元する）とは
 * **別経路**として、`breeding_result` を [JdbcClient] で直接集計し、 集約を一切組まずに平坦な [BreedingResultSummaryView]
 * へ詰める。
 *
 * 指標は JAIRS 様式第2号（繁殖登録原簿〈雄〉）/ 様式第14号裏の定義に従う:
 * - 種付雌馬数 = `WHERE covering_stallion_id = :stallionId` の件数（種付せず＝`covering_stallion_id IS NULL`
 *   は自然に除外）
 * - 受胎数 = 報告済みかつ `NOT_CONCEIVED` でない件数（流産・死産・生後直死は受胎に含む）
 * - 生産数 = `LIVE_FOAL` のみ（生後直死は「産駒がない母」に分類され生産に含めない）
 *
 * 受胎率・生産率は SQL で割らず [BreedingResultSummaryView.of] が件数から算出する（H2/PostgreSQL の 浮動小数・丸めの差を避ける）。 `GROUP
 * BY` 後の行は種付雌馬数が常に 1 以上のため 0 除算は起きない。 `COUNT(*) FILTER (WHERE ...)` は PostgreSQL / H2 双方が対応する。
 */
@Repository
class JdbcBreedingResultSummaryQueries(private val jdbcClient: JdbcClient) :
    BreedingResultSummaryQueries {

    override fun findByStallion(stallionId: BloodHorseId): List<BreedingResultSummaryView> =
        jdbcClient
            .sql(
                """
                SELECT
                    covering_stallion_id AS stallion_id,
                    breeding_year,
                    COUNT(*) AS mares_covered,
                    COUNT(*) FILTER (
                        WHERE outcome_type IS NOT NULL
                        AND outcome_type <> 'NOT_CONCEIVED'
                        AND outcome_type <> 'NOT_COVERED'
                    ) AS conceived,
                    COUNT(*) FILTER (WHERE outcome_type = 'LIVE_FOAL') AS live_foals
                FROM breeding_result
                WHERE covering_stallion_id = :stallionId
                GROUP BY covering_stallion_id, breeding_year
                ORDER BY breeding_year
                """
                    .trimIndent()
            )
            .param("stallionId", stallionId.value)
            .query { rs, _ ->
                BreedingResultSummaryView.of(
                    stallionId = rs.getObject("stallion_id", UUID::class.java),
                    breedingYear = rs.getInt("breeding_year"),
                    maresCovered = rs.getInt("mares_covered"),
                    conceived = rs.getInt("conceived"),
                    liveFoals = rs.getInt("live_foals"),
                )
            }
            .list()
}
