package com.example.api.domain.horseracing

import org.junit.jupiter.api.DisplayName
import kotlin.test.Test

class JockeyTest {
    @Test
    @DisplayName("コンストラクタ")
    fun constructorSetsPropertiesCorrectly() {
        val jockey = Jockey("武", "豊")
        assert(jockey.firstName == "武")
        assert(jockey.lastName == "豊")
    }
}
