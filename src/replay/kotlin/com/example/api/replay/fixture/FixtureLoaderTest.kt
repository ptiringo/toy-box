package com.example.api.replay.fixture

import org.junit.jupiter.api.Test

class FixtureLoaderTest {
    @Test
    fun `種付ありの実在馬フィクスチャを公開事実と合成値の2層で読み込める`() {
        val fixture = FixtureLoader.load("01-imported-normal.json") as CoveredSeasonFixture

        assert(fixture.name.contains("ウインドインハーヘア"))
        assert(fixture.sources.broodmare.startsWith("https://www.jbis.or.jp/horse/0000430846/"))
        assert(fixture.facts.coveringYear == 2001)
        assert(fixture.facts.broodmare.originCountry == "アイルランド")
        assert(fixture.facts.foal?.name == "ディープインパクト")
        assert(fixture.notes.isNotEmpty())
        assert(fixture.synthesized.foal?.dnaParentage == "CONSISTENT")
    }

    @Test
    fun `manifest に列挙した全フィクスチャを読み込める`() {
        val fixtures = FixtureLoader.loadAll()

        assert(fixtures.size == 6)
        val covered = fixtures.filterIsInstance<CoveredSeasonFixture>()
        // 内国産の基礎馬は facts に originCountry を持たない（合成側で輸入馬扱いにする）。
        assert(covered.any { it.facts.broodmare.originCountry == null })
        // 未命名の産駒がある（血統登録のみで馬名登録が未了）。
        assert(covered.any { it.facts.foal != null && it.facts.foal.name == null })
    }

    @Test
    fun `種付なしの年のフィクスチャは種牡馬も種付証明も持たない`() {
        val fixture = FixtureLoader.load("06-uncovered-season.json") as UncoveredSeasonFixture

        assert(fixture.name.contains("テスコパール"))
        assert(fixture.facts.breedingYear == 1999)
        assert(fixture.sources.stallion == null)
        assert(fixture.notes.isNotEmpty())
        assert(fixture.synthesized.submissions.breedingReportSubmittedOn == "2000-05-25")
    }
}
