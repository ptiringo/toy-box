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
        // 公開されているのは JBIS の表示年（＝産駒の生年）で、種付年はそこからの換算＝合成値。
        assert(fixture.facts.displayedYear == 2002)
        assert(fixture.synthesized.coveringYear == 2001)
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
        // 内国産の基礎馬も出生国は事実（「日本」）として facts に持つ（#633）。
        assert(covered.any { it.facts.broodmare.originCountry == "日本" })
        // 未命名の産駒がある（血統登録のみで馬名登録が未了）。
        assert(covered.any { it.facts.foal != null && it.facts.foal.name == null })
        // 種付なしの年がある。
        assert(fixtures.any { it is UncoveredSeasonFixture })
    }

    @Test
    fun `内国産馬の出生国は合成ではなく事実として facts に載る`() {
        // 出生国は JBIS の産地から分かる事実なので、内国産馬（移行取り込み経路で seed する馬）でも
        // 合成層には置かない。置くと「facts に出生国が無い馬に嘘の出生国を書ける」余地が残る。
        val fixture = FixtureLoader.load("02-domestic-barren.json") as CoveredSeasonFixture

        assert(fixture.facts.broodmare.originCountry == "日本")
        // 移行取り込み経路（RegisterCarriedOverHorse）で seed するため揚陸日を持たない（#633）。
        assert(fixture.synthesized.broodmare.landingDate == null)
    }

    @Test
    fun `合成した年は表示年から 1 を引いた換算値になっている`() {
        // 換算（表示年 − 1）は合成の判断なので synthesized 側にある。facts 側の表示年と食い違えば
        // フィクスチャの記述ミス（合成の理由は notes に書かれている前提が崩れる）。
        val fixtures = FixtureLoader.loadAll()

        for (fixture in fixtures.filterIsInstance<CoveredSeasonFixture>()) {
            assert(fixture.synthesized.coveringYear == fixture.facts.displayedYear - 1) {
                "種付年の換算が表示年 − 1 でない: ${fixture.name}"
            }
        }
        for (fixture in fixtures.filterIsInstance<UncoveredSeasonFixture>()) {
            assert(fixture.synthesized.breedingYear == fixture.facts.displayedYear - 1) {
                "繁殖年の換算が表示年 − 1 でない: ${fixture.name}"
            }
        }
    }

    @Test
    fun `種付なしの年のフィクスチャは種牡馬も種付証明も持たない`() {
        val fixture = FixtureLoader.load("06-uncovered-season.json") as UncoveredSeasonFixture

        assert(fixture.name.contains("テスコパール"))
        // 公開事実は「表示年 2000 の行が産駒なし・種牡馬欄が空」。繁殖年 1999 はそこからの換算＝合成値。
        assert(fixture.facts.displayedYear == 2000)
        assert(fixture.synthesized.breedingYear == 1999)
        assert(fixture.sources.stallion == null)
        assert(fixture.notes.isNotEmpty())
        assert(fixture.synthesized.submissions.breedingReportSubmittedOn == "2000-05-25")
    }
}
