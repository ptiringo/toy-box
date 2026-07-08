package com.example.api.domain.sakamichi.model.release

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import org.jmolecules.ddd.annotation.ValueObject

/** 曲名が不変条件（非ブランク・100 文字以内）を満たさない。 */
data object InvalidTrackTitle

/**
 * 曲名（作品の収録曲 1 曲の名。例: 左胸の勇気）。非ブランク・100 文字以内を不変条件とする。
 *
 * 表題曲・リード曲・カップリング曲・アルバム曲を区別せず、全トラック共通の曲名 VO として持つ （見出し曲は [Track] の一種として [Tracklist] に含める。旧
 * SingleTitle / AlbumTitle を統合）。
 *
 * @property value 曲名
 */
@ValueObject
@JvmInline
value class TrackTitle private constructor(val value: String) {
    companion object {
        private const val MAX_LENGTH = 100

        /** 非ブランク・100 文字以内であることを検証して [TrackTitle] を生成する。 */
        fun create(value: String): Result<TrackTitle, InvalidTrackTitle> {
            val trimmed = value.trim()
            return if (trimmed.isNotEmpty() && trimmed.length <= MAX_LENGTH) {
                Ok(TrackTitle(trimmed))
            } else {
                Err(InvalidTrackTitle)
            }
        }
    }
}
