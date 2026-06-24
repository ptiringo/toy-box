package com.example.api.domain.studbook.model.horse.bloodhorse

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import org.jmolecules.ddd.annotation.ValueObject

/** 馬名が不変条件（カタカナ2〜9文字）を満たさない。 */
data object InvalidHorseName

/**
 * 馬名。
 *
 * 血統登録された軽種馬に後から付与される競走馬名。日本軽種馬登録協会（JAIRS）「馬名登録実施基準」に基づき、 **片仮名 2〜9 文字（長音符「ー」を含む）** を本 VO の不変条件とする。
 * - 文字数: 同基準は「1 字又は 10 字以上の馬名はつけられません」と定めるため、下限 2・上限 9 とする。
 * - 文字種: 片仮名遣いは内閣告示「現代仮名遣い」「外来語の表記」に準拠（直音・拗音・撥音・促音・外来語表記の小書き等）。
 *   長音は長音符「ー」を用いる。漢字・ひらがな・英数・半角カナ・記号（中黒「・」含む）は受け付けない。
 *
 * ここで検証するのは **形式の不変条件のみ**。馬名登録原簿との重複・保護馬名（GI 勝馬名・種牡馬/種牝馬名・父母名等）の照合、
 * 奇矯・公序良俗・広告等の審査、輸入馬の文字数例外は、レジストリ参照や審査を要する業務ルールであり本 VO の責務外とする （必要になった時点でポート／ユースケース側に切り出す）。
 *
 * @property value 片仮名 2〜9 文字からなる馬名
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
