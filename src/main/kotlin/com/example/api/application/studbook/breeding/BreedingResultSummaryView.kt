package com.example.api.application.studbook.breeding

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID
import org.jmolecules.architecture.cqrs.QueryModel

/**
 * 繁殖成績の年次集計の読み取りモデル（軽量 CQRS / L2。ADR-0031）。
 *
 * JAIRS 様式第2号（繁殖登録原簿〈雄〉）に対応し、(種牡馬, 種付年) 単位の繁殖成績を表す。 書き込み集約
 * [com.example.api.domain.studbook.model.breeding.BreedingResult] を一切経由せず、ストアから直接組む 平坦な DTO。
 *
 * `@QueryModel`（jMolecules CQRS）で読み取りモデルとしての役割を表明する。不変条件は持たない。 受胎率・生産率は件数から導出する派生値のため、
 * 整合しない率を直接渡せないよう **コンストラクタは private とし、生成は件数から率を算出する [of] ファクトリ経由に限る**。
 *
 * 指標定義（様式第2号 / 様式第14号裏）:
 * - [maresCovered] 種付雌馬数 = その種牡馬がその年に種付けした雌馬数
 * - [conceived] 受胎数 = 種付雌馬数のうち受胎が確認された件数（不受胎を除く）
 * - [liveFoals] 生産数 = 生存産駒を得た件数（生後直死は「産駒がない母」に分類され含めない）
 * - [conceptionRate] 受胎率(%) = 受胎数 / 種付雌馬数、[productionRate] 生産率(%) = 生産数 / 種付雌馬数
 *
 * 分娩結果が未報告（outcome == null）の行は [maresCovered]（分母）にのみ計上され、[conceived] / [liveFoals] には含まれない。
 * したがって年内の分娩結果が全て報告されるまで、[conceptionRate] / [productionRate] は報告済み時点までの暫定値となる。
 *
 * @property stallionId 種牡馬の生 UUID（`covering.stallionId`）
 * @property breedingYear 種付年
 */
@QueryModel
@ConsistentCopyVisibility
data class BreedingResultSummaryView
private constructor(
    val stallionId: UUID,
    val breedingYear: Int,
    val maresCovered: Int,
    val conceived: Int,
    val liveFoals: Int,
    val conceptionRate: BigDecimal,
    val productionRate: BigDecimal,
) {
    companion object {
        private const val PERCENTAGE_SCALE = 100
        private const val PERCENTAGE_DECIMAL_PLACES = 1

        /**
         * 件数から受胎率・生産率（百分率・小数1桁・HALF_UP）を算出して View を組む。 集計後の GROUP 行では [maresCovered] が常に 1 以上のため 0
         * 除算は起きない。
         */
        fun of(
            stallionId: UUID,
            breedingYear: Int,
            maresCovered: Int,
            conceived: Int,
            liveFoals: Int,
        ): BreedingResultSummaryView =
            BreedingResultSummaryView(
                stallionId = stallionId,
                breedingYear = breedingYear,
                maresCovered = maresCovered,
                conceived = conceived,
                liveFoals = liveFoals,
                conceptionRate = percentage(conceived, maresCovered),
                productionRate = percentage(liveFoals, maresCovered),
            )

        private fun percentage(numerator: Int, denominator: Int): BigDecimal =
            BigDecimal(numerator)
                .multiply(BigDecimal(PERCENTAGE_SCALE))
                .divide(BigDecimal(denominator), PERCENTAGE_DECIMAL_PLACES, RoundingMode.HALF_UP)
    }
}
