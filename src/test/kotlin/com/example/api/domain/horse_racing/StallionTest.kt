package com.example.api.domain.horse_racing

import kotlin.test.Test

/**
 * Stallion クラスのユニットテスト
 */
class StallionTest {
    @Test
    fun 馬名が正しく設定されている場合はその値が取得できる() {
        val stallion = Stallion(name = "ディープインパクト")
        assert(stallion.name == "ディープインパクト")
    }
}
