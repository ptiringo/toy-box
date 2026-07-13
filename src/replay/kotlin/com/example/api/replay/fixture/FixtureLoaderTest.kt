package com.example.api.replay.fixture

import org.junit.jupiter.api.Test

class FixtureLoaderTest {
    @Test
    fun `実在馬フィクスチャを公開事実と合成値の2層で読み込める`() {
        val fixture = FixtureLoader.load("01-imported-normal.json")

        assert(fixture.name.contains("ウインドインハーヘア"))
        assert(fixture.sources.broodmare.startsWith("https://www.jbis.or.jp/horse/0000430846/"))
        assert(fixture.facts.coveringYear == 2001)
        assert(fixture.facts.broodmare.originCountry == "アイルランド")
        assert(fixture.facts.foal?.name == "ディープインパクト")
        assert(fixture.synthesized.notes.isNotEmpty())
        assert(fixture.synthesized.foal?.dnaParentage == "CONSISTENT")
    }

    @Test
    fun `manifest に列挙した全フィクスチャを読み込める`() {
        val fixtures = FixtureLoader.loadAll()

        assert(fixtures.size == 5)
        // 内国産の基礎馬は facts に originCountry を持たない（合成側で輸入馬扱いにする）。
        assert(fixtures.any { it.facts.broodmare.originCountry == null })
        // 未命名の産駒がある（血統登録のみで馬名登録が未了）。
        assert(fixtures.any { it.facts.foal != null && it.facts.foal.name == null })
    }
}
