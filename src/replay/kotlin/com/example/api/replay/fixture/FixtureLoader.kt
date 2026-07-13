package com.example.api.replay.fixture

import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

/** クラスパス上のフィクスチャ JSON ファイルを [HorseFixture] へ読み込む。 */
object FixtureLoader {
    private val mapper = JsonMapper.builder().build().registerKotlinModule()

    /** 単一フィクスチャを名前で読み込む（例: "01-imported-normal.json"）。JSON の "kind" で covered / uncovered を判別する。 */
    fun load(resourceName: String): HorseFixture {
        val stream =
            requireNotNull(javaClass.classLoader.getResourceAsStream("fixtures/$resourceName")) {
                "フィクスチャが見つからない: fixtures/$resourceName"
            }
        return stream.use { mapper.readValue(it) }
    }

    /** fixtures/manifest.txt に列挙されたフィクスチャ名（`#` 始まりのコメントと空行は除く）。件数の唯一の出所。 */
    fun manifestNames(): List<String> {
        val manifest =
            requireNotNull(javaClass.classLoader.getResourceAsStream("fixtures/manifest.txt")) {
                "fixtures/manifest.txt が見つからない"
            }
        val names = manifest.use { it.bufferedReader().readLines() }
        return names.map(String::trim).filter { it.isNotEmpty() && !it.startsWith("#") }
    }

    /** fixtures/manifest.txt に列挙された全フィクスチャを読み込む。 */
    fun loadAll(): List<HorseFixture> = manifestNames().map(::load)
}
