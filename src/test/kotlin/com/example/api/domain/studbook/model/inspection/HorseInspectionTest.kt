package com.example.api.domain.studbook.model.inspection

import com.github.michaelbull.result.unwrap
import org.junit.jupiter.api.Test

/** [HorseInspection]（審査集約）のユニットテスト */
class HorseInspectionTest {
    private val microchip = MicrochipNumber.create("392140000000001").unwrap()

    @Test
    fun `審査を生成すると ID が採番され識別子と親子判定を保持する`() {
        val parentage = ParentageDetermination.ByDna(DnaParentageResult.CONSISTENT)

        val inspection = HorseInspection.create(microchip, parentage)

        assert(inspection.microchipNumber == microchip)
        assert(inspection.parentage == parentage)
        assert(inspection.features == null)
    }

    @Test
    fun `同じ ID なら等価であること`() {
        val parentage = ParentageDetermination.NotApplicable
        val features =
            IdentificationFeatures(hairWhorl = "額", whiteMarkings = null, nosePrint = null)
        val original = HorseInspection.create(microchip, parentage, features)

        val restored = HorseInspection.reconstitute(original.id, microchip, parentage, features)

        assert(original == restored)
        assert(restored.features == features)
    }
}
