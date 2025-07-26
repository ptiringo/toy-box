package com.example.api.domain.horse_racing

import kotlin.test.Test

class RacehorseTest {

    @Test
    fun コンストラクタでプロパティが正しく設定されること() {
        val racehorse = Racehorse("クロワデュノール")
        assert(racehorse.name == "クロワデュノール")
    }
}
