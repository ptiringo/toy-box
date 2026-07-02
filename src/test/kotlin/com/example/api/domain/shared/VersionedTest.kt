package com.example.api.domain.shared

import org.junit.jupiter.api.Test

class VersionedTest {

    @Test
    fun `mapは値だけを写しversionを引き継ぐ`() {
        val versioned = Versioned("before", 3L)

        val mapped = versioned.map { it.length }

        assert(mapped.value == 6)
        assert(mapped.version == 3L)
    }

    @Test
    fun `同じ値と同じversionのVersionedは等価`() {
        assert(Versioned("a", 1L) == Versioned("a", 1L))
        assert(Versioned("a", 1L) != Versioned("a", 2L))
    }
}
