package com.example.api.domain.horseracing.model.breeding

import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import org.junit.jupiter.api.Test

/** [BreedingRegion] のユニットテスト */
class BreedingRegionTest {
    @Test
    fun `ブランクでない区域名なら生成できること`() {
        val region = BreedingRegion.create("北海道").unwrap()

        assert(region.value == "北海道")
    }

    @Test
    fun `前後の空白は trim されること`() {
        val region = BreedingRegion.create("  北海道  ").unwrap()

        assert(region.value == "北海道")
    }

    @Test
    fun `ブランクだと BlankBreedingRegion を返すこと`() {
        val result = BreedingRegion.create("   ")

        assert(result.getError() == BlankBreedingRegion)
    }
}
