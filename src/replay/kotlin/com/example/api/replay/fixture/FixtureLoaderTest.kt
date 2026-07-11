package com.example.api.replay.fixture

import org.junit.jupiter.api.Test

class FixtureLoaderTest {
    @Test
    fun `正常系フィクスチャを読み込める`() {
        val fixture = FixtureLoader.load("01-normal.json")
        assert(fixture.name == "サンプルノーマル")
        assert(fixture.coveringYear == 2019)
        assert(fixture.stallion.sex == "MALE")
        assert(fixture.broodmare.sex == "FEMALE")
        assert(fixture.foaling.outcome == "LiveFoal")
        assert(fixture.foal?.name == "サンプルコウマ")
    }

    @Test
    fun `manifest 経由で全フィクスチャを読み込める`() {
        val all = FixtureLoader.loadAll()
        assert(all.isNotEmpty())
        assert(all.any { it.name == "サンプルノーマル" })
    }
}
