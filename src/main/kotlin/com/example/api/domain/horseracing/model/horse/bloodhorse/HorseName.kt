package com.example.api.domain.horseracing.model.horse.bloodhorse

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import org.jmolecules.ddd.annotation.ValueObject

/** 馬名が不変条件（カタカナ2〜9文字）を満たさない。 */
data object InvalidHorseName

/**
 * 馬名。
 *
 * 血統登録された軽種馬に後から付与される競走馬名。JRA / 日本軽種馬登録協会の命名規則に倣い、 カタカナ 2〜9 文字（長音符「ー」を含む）を不変条件とする。記号・漢字・空白などは
 * 受け付けない（規則の細目が判明したら段階的に厳格化する）。
 *
 * @property value カタカナ 2〜9 文字からなる馬名
 */
@ValueObject
@JvmInline
value class HorseName private constructor(val value: String) {
    companion object {
        private val LENGTH = 2..9

        /** 長音符。カタカナ表記の一部として馬名に用いられる。 */
        private const val PROLONGED_SOUND_MARK = 'ー'

        /** カタカナ（小書き「ァ」〜「ヺ」）。長音符は [PROLONGED_SOUND_MARK] で別途許可する。 */
        private val KATAKANA = 'ァ'..'ヺ'

        private fun Char.isKatakana(): Boolean = this in KATAKANA || this == PROLONGED_SOUND_MARK

        /** カタカナ 2〜9 文字であることを検証して [HorseName] を生成する。 */
        fun create(value: String): Result<HorseName, InvalidHorseName> {
            val trimmed = value.trim()
            return if (trimmed.length in LENGTH && trimmed.all { it.isKatakana() }) {
                Ok(HorseName(trimmed))
            } else {
                Err(InvalidHorseName)
            }
        }
    }
}
