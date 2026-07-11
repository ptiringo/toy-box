package com.example.api.replay.fixture

import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

object FixtureLoader {
    private val mapper = JsonMapper.builder().build().registerKotlinModule()

    fun load(resourceName: String): HorseFixture {
        val stream =
            requireNotNull(javaClass.classLoader.getResourceAsStream("fixtures/$resourceName")) {
                "Fixture not found: fixtures/$resourceName"
            }
        return stream.use { mapper.readValue(it) }
    }

    fun loadAll(): List<HorseFixture> {
        val manifest =
            requireNotNull(javaClass.classLoader.getResourceAsStream("fixtures/manifest.txt")) {
                "fixtures/manifest.txt not found"
            }
        val names = manifest.use { it.bufferedReader().readLines() }
        return names.map(String::trim).filter { it.isNotEmpty() && !it.startsWith("#") }.map(::load)
    }
}
