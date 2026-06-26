package com.example.api.infrastructure.studbook.breeding

import com.example.api.domain.shared.generateId
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseId
import com.example.api.support.PostgresContainerSupport
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.context.TestConstructor
import org.springframework.test.context.TestConstructor.AutowireMode

/**
 * 読み取りポート [com.example.api.application.studbook.breeding.BreedingResultSummaryQueries] の Spring
 * Data JDBC とは別経路の集計実装 [JdbcBreedingResultSummaryQueries] の契約テスト（軽量 CQRS / L2。ADR-0031）。
 *
 * 本番ターゲットの PostgreSQL（Testcontainers、[PostgresContainerSupport] で共有）に対し、GROUP BY 集計・ FILTER 区分・率算出が
 * JAIRS 様式の定義（生後直死は生産に含めない／種付せずは分母に入らない／未報告は 受胎・生産に入らない）どおりかを検証する。行の投入は infrastructure 内部の
 * [BreedingResultSpringDataRepository] で行い、読み取りは別経路の [JdbcBreedingResultSummaryQueries] で引く。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestConstructor(autowireMode = AutowireMode.ALL)
class JdbcBreedingResultSummaryQueriesContractTest(
    private val jdbcClient: JdbcClient,
    private val rows: BreedingResultSpringDataRepository,
) : PostgresContainerSupport() {

    private val queries = JdbcBreedingResultSummaryQueries(jdbcClient)

    private val stallion = generateId()
    private val otherStallion = generateId()

    @BeforeEach
    fun cleanUp() {
        rows.deleteAll()
    }

    /** 種付あり行を作る。outcomeType=null は未報告。LIVE_FOAL のみ分娩日を持つ。 */
    private fun covered(
        stallionId: UUID,
        year: Int,
        outcomeType: String?,
        foalingDate: LocalDate? = null,
    ) =
        BreedingResultRow(
            id = generateId(),
            breedingRegistrationId = generateId(),
            breedingYear = year,
            coveringStallionId = stallionId,
            coveringDate = LocalDate.of(year, 4, 1),
            coveringPlace = "北海道",
            coveringCertificateNumber = "C-$year-${generateId()}",
            outcomeType = outcomeType,
            outcomeFoalingDate = foalingDate,
        )

    /** 種付せず行（covering 列は全 null、outcomeType は NOT_COVERED 固定）。 */
    private fun notCovered(year: Int) =
        BreedingResultRow(
            id = generateId(),
            breedingRegistrationId = generateId(),
            breedingYear = year,
            outcomeType = "NOT_COVERED",
        )

    @Test
    fun `種付年単位で種付雌馬数・受胎数・生産数と率を集計する`() {
        // 2024年・対象種牡馬: 種付7頭（双子流産を1頭追加して TWIN 区分の集計も検証）
        rows.saveAll(
            listOf(
                covered(stallion, 2024, "LIVE_FOAL", LocalDate.of(2025, 3, 1)), // 受胎○ 生産○
                covered(stallion, 2024, "NEONATAL_DEATH"), // 受胎○ 生産×（生後直死）
                covered(stallion, 2024, "ABORTION"), // 受胎○ 生産×
                covered(stallion, 2024, "STILLBIRTH"), // 受胎○ 生産×
                covered(stallion, 2024, "NOT_CONCEIVED"), // 受胎× 生産×
                covered(stallion, 2024, null), // 未報告: 分母のみ
                covered(stallion, 2024, "TWIN_ABORTION"), // 受胎○ 生産×（双子流産）
            )
        )
        // 除外されるべき行
        rows.save(notCovered(2024)) // 種付せず → 分母に入らない
        rows.save(covered(otherStallion, 2024, "LIVE_FOAL", LocalDate.of(2025, 3, 2))) // 別種牡馬

        val summaries = queries.findByStallion(BloodHorseId(stallion))

        assert(summaries.size == 1)
        val s = summaries.single()
        assert(s.stallionId == stallion)
        assert(s.breedingYear == 2024)
        // 6区分 + TWIN_ABORTION = 7頭
        assert(s.maresCovered == 7)
        // 不受胎（NOT_CONCEIVED）と未報告を除く（TWIN_ABORTION は受胎に含む）
        assert(s.conceived == 5)
        // LIVE_FOAL のみ（生後直死・双子流産は含めない）
        assert(s.liveFoals == 1)
        // 受胎率 5/7=71.4%、生産率 1/7=14.3%
        assert(s.conceptionRate.compareTo(BigDecimal("71.4")) == 0)
        assert(s.productionRate.compareTo(BigDecimal("14.3")) == 0)
    }

    @Test
    fun `複数の種付年を年昇順で返す`() {
        rows.saveAll(
            listOf(
                covered(stallion, 2024, "LIVE_FOAL", LocalDate.of(2025, 3, 1)),
                covered(stallion, 2023, "NOT_CONCEIVED"),
            )
        )

        val years = queries.findByStallion(BloodHorseId(stallion)).map { it.breedingYear }

        assert(years == listOf(2023, 2024))
    }

    @Test
    fun `該当する種牡馬の成績が無ければ空リストを返す`() {
        assert(queries.findByStallion(BloodHorseId(generateId())).isEmpty())
    }
}
