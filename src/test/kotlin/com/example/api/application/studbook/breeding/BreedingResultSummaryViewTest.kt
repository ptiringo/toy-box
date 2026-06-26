package com.example.api.application.studbook.breeding

import java.math.BigDecimal
import java.util.UUID
import org.junit.jupiter.api.Test

class BreedingResultSummaryViewTest {

    private val stallionId = UUID.fromString("11111111-1111-1111-1111-111111111111")

    @Test
    fun `件数から受胎率・生産率を百分率小数1桁で算出する`() {
        // 種付10・受胎8・生産6 → 受胎率80.0% 生産率60.0%
        val view = BreedingResultSummaryView.of(stallionId, 2024, 10, 8, 6)

        assert(view.maresCovered == 10)
        assert(view.conceived == 8)
        assert(view.liveFoals == 6)
        assert(view.conceptionRate.compareTo(BigDecimal("80.0")) == 0)
        assert(view.productionRate.compareTo(BigDecimal("60.0")) == 0)
    }

    @Test
    fun `割り切れない率はHALF_UPで小数1桁に丸める`() {
        // 種付3・受胎2 → 66.666...% → 66.7%
        val view = BreedingResultSummaryView.of(stallionId, 2024, 3, 2, 1)

        assert(view.conceptionRate.compareTo(BigDecimal("66.7")) == 0)
        // 生産1/3 = 33.333...% → 33.3%
        assert(view.productionRate.compareTo(BigDecimal("33.3")) == 0)
    }

    @Test
    fun `受胎・生産が0なら率は0_0`() {
        val view = BreedingResultSummaryView.of(stallionId, 2024, 5, 0, 0)

        assert(view.conceptionRate.compareTo(BigDecimal("0.0")) == 0)
        assert(view.productionRate.compareTo(BigDecimal("0.0")) == 0)
    }
}
