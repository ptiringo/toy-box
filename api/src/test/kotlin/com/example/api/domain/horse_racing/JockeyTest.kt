package com.example.api.domain.horse_racing

import org.junit.jupiter.api.DisplayName
import kotlin.test.Test

class JockeyTest {

    @Test
    @DisplayName("コンストラクタ")
    fun コンストラクタでプロパティが正しく設定されること() {
        val jockey = Jockey("武", "豊")
        assert(jockey.firstName == "武")
        assert(jockey.lastName == "豊")
    }
}
