package com.example.api.domain.sakamichi.model.album

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import org.jmolecules.ddd.annotation.ValueObject

/** アルバムのリード曲名が不変条件（非ブランク・100 文字以内）を満たさない。 */
data object InvalidAlbumTitle

/**
 * アルバムのリード曲名（アルバムの代表曲＝リード曲の曲名。例: 僕は僕を好きになる）。 非ブランク・100 文字以内を不変条件とする。
 *
 * シングルの表題曲（[com.example.api.domain.sakamichi.model.single.SingleTitle]）とは呼称が異なる別概念のため、 構造が同一でも別 VO
 * として持つ。
 *
 * @property value リード曲名
 */
@ValueObject
@JvmInline
value class AlbumTitle private constructor(val value: String) {
    companion object {
        private const val MAX_LENGTH = 100

        /** 非ブランク・100 文字以内であることを検証して [AlbumTitle] を生成する。 */
        fun create(value: String): Result<AlbumTitle, InvalidAlbumTitle> {
            val trimmed = value.trim()
            return if (trimmed.isNotEmpty() && trimmed.length <= MAX_LENGTH) {
                Ok(AlbumTitle(trimmed))
            } else {
                Err(InvalidAlbumTitle)
            }
        }
    }
}
