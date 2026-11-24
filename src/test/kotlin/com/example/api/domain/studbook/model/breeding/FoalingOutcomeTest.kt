package com.example.api.domain.studbook.model.breeding

import java.time.LocalDate
import org.junit.jupiter.api.Test

/** [FoalingOutcome.LiveFoal] の双子（様式第14号「産駒あり」の双子欄）に関するユニットテスト。 */
class FoalingOutcomeTest {
    private val foalingDate = LocalDate.of(1991, 3, 20)

    @Test
    fun `双子なしの生産は twin が null`() {
        assert(FoalingOutcome.LiveFoal(foalingDate).twin == null)
    }

    @Test
    fun `双子の生産はもう一頭の分娩日を持つ`() {
        val live = FoalingOutcome.LiveFoal(foalingDate, TwinFoal(foalingDate.plusDays(1)))

        assert(live.twin == TwinFoal(LocalDate.of(1991, 3, 21)))
    }
}
