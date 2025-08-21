package com.example.api.domain.horse_racing.race

import kotlin.test.Test

/**
 * Race クラスのユニットテスト
 */
class RaceTest {
    @Test
    fun レースIDとレース名が正しく設定されている場合はその値が取得できる() {
        val race = Race(id = RaceId(1L), name = "日本ダービー")
        assert(race.id.value == 1L)
        assert(race.name == "日本ダービー")
    }
}
