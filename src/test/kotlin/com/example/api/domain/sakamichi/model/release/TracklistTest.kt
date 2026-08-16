package com.example.api.domain.sakamichi.model.release

import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import org.junit.jupiter.api.Test

/** Tracklist 値オブジェクトの構築時不変条件（空でない・番号が 1..n の連番・重複なし）のユニットテスト。 */
class TracklistTest {
    private fun number(value: Int): TrackNumber = TrackNumber.create(value).unwrap()

    private fun track(number: Int, title: String): Track =
        Track(number(number), TrackTitle.create(title).unwrap())

    @Test
    fun `1からnの連番なら生成できる`() {
        val tracklist = Tracklist.create(listOf(track(1, "表題曲"), track(2, "カップリング"))).unwrap()

        assert(tracklist.tracks.size == 2)
    }

    @Test
    fun `曲名が重複しても生成できる`() {
        val tracklist = Tracklist.create(listOf(track(1, "同名曲"), track(2, "同名曲"))).unwrap()

        assert(tracklist.tracks.size == 2)
    }

    @Test
    fun `空なら Empty を返す`() {
        assert(Tracklist.create(emptyList()).getError() == TracklistError.Empty)
    }

    @Test
    fun `番号が重複すると DuplicateNumber を返す`() {
        val error = Tracklist.create(listOf(track(1, "A"), track(1, "B"))).getError()

        assert(error == TracklistError.DuplicateNumber(setOf(number(1))))
    }

    @Test
    fun `1始まりでないと NonContiguousNumbers を返す`() {
        val error = Tracklist.create(listOf(track(2, "A"), track(3, "B"))).getError()

        assert(error == TracklistError.NonContiguousNumbers(setOf(number(2), number(3))))
    }

    @Test
    fun `欠番があると NonContiguousNumbers を返す`() {
        val error = Tracklist.create(listOf(track(1, "A"), track(3, "B"))).getError()

        assert(error == TracklistError.NonContiguousNumbers(setOf(number(1), number(3))))
    }

    @Test
    fun `トラック番号の昇順に整列される`() {
        val tracklist = Tracklist.create(listOf(track(2, "B"), track(1, "A"))).unwrap()

        assert(tracklist.tracks.map { it.number.value } == listOf(1, 2))
    }
}
