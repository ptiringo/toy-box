package com.example.api.domain.sakamichi.model.release

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import org.jmolecules.ddd.annotation.ValueObject

/**
 * 収録曲一覧（[Tracklist.create]）の構築時不変条件違反。
 *
 * 失敗のしかたが複数あるため sealed interface とし、`when` の網羅性で漏れを防ぐ。
 */
sealed interface TracklistError {
    /** 収録曲が空（作品は最低 1 曲＝見出し曲を持つ）。 */
    data object Empty : TracklistError

    /**
     * トラック番号が重複している。
     *
     * @property values 重複しているトラック番号の値の集合
     */
    data class DuplicateNumber(val values: Set<Int>) : TracklistError

    /**
     * トラック番号の集合が 1..n（n = 曲数）と一致しない（1 始まりでない・欠番がある等）。
     *
     * @property values 実際に与えられたトラック番号の値の集合
     */
    data class NonContiguousNumbers(val values: Set<Int>) : TracklistError
}

/**
 * 収録曲一覧（トラックリスト）。作品（シングル/アルバム）の全収録曲を通し番号で保持する値オブジェクト。
 *
 * 見出し曲（表題曲/リード曲）も [Track] の一種として本一覧に含める。順序はトラック番号で定まる。 不変条件（空でない・番号が 1..n の連番・重複なし）は生成ファクトリ
 * [create] が検証する（ADR-0014）。 曲名の重複は許容する（別バージョン等の余地を残す）。
 *
 * @property tracks 収録曲の並び
 */
@ValueObject
@ConsistentCopyVisibility
data class Tracklist private constructor(val tracks: List<Track>) {
    companion object {
        /**
         * 不変条件（空でない・番号が 1..n の連番・重複なし）を検証して [Tracklist] を生成する。
         *
         * @param tracks 収録曲の並び
         * @return 検証済みの [Tracklist]、または不変条件違反を表す [TracklistError]
         */
        fun create(tracks: List<Track>): Result<Tracklist, TracklistError> {
            val duplicates = tracks.groupBy { it.number.value }.filterValues { it.size > 1 }.keys
            val actualNumbers = tracks.map { it.number.value }.toSet()
            val expectedNumbers = (1..tracks.size).toSet()
            return when {
                tracks.isEmpty() -> Err(TracklistError.Empty)
                duplicates.isNotEmpty() -> Err(TracklistError.DuplicateNumber(duplicates))
                actualNumbers != expectedNumbers ->
                    Err(TracklistError.NonContiguousNumbers(actualNumbers))
                else -> Ok(Tracklist(tracks))
            }
        }
    }
}
