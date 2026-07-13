package com.example.api.replay.fixture

import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
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
        val declared = FixtureLoader.manifestNames()
        val fixtures = FixtureLoader.loadAll()

        // 件数はここに書かない（manifest が唯一の出所。フィクスチャを足しても 2 箇所直さずに済む）。
        assert(declared.isNotEmpty())
        // fixtures/loadAll は manifestNames().map(::load) なので size 比較はトートロジーで空振りする。
        // 検出したいのは「JSON を置いたのに manifest に書き忘れた（＝永久に replay されない死にデータ）」なので、
        // クラスパス上の fixtures/*.json の集合と manifest の集合そのものを突合する。
        val files =
            Path.of(requireNotNull(javaClass.classLoader.getResource("fixtures")).toURI())
                .listDirectoryEntries("*.json")
                .map { it.fileName.toString() }
        assert(files.toSet() == declared.toSet()) {
            "manifest と fixtures/*.json が不一致: $files vs $declared"
        }
        val covered = fixtures.filterIsInstance<CoveredSeasonFixture>()
        // 内国産の基礎馬は facts に originCountry を持たない（合成側で輸入馬扱いにする）。
        assert(covered.any { it.facts.broodmare.originCountry == null })
        // 未命名の産駒がある（血統登録のみで馬名登録が未了）。
        assert(covered.any { it.facts.foal != null && it.facts.foal.name == null })
        // 種付なしの年がある。
        assert(fixtures.any { it is UncoveredSeasonFixture })
    }

    @Test
    fun `輸入馬は事実の出生国を使うので合成側の出生国を持たない`() {
        val fixture = FixtureLoader.load("01-imported-normal.json") as CoveredSeasonFixture

        // facts に出生国がある馬で synthesized 側にも書けると、食い違っても誰も気づかない死にデータになる。
        assert(fixture.facts.broodmare.originCountry == "アイルランド")
        assert(fixture.synthesized.broodmare.originCountry == null)
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
