package com.example.api.domain.studbook.model.horse.bloodhorse

import com.example.api.domain.studbook.model.breeding.BreedingResultId
import org.jmolecules.ddd.annotation.ValueObject

/**
 * 産駒が繁殖成績の中で占める枠。
 *
 * 繁殖成績報告書（様式第14号）は、生産の産駒を表の「産駒あり」欄に、双子のもう一頭を裏の「双子の繁殖成績」欄に書く。
 * 1つの繁殖成績から血統登録できる産駒は、双子なら2頭（[PRIMARY]と[TWIN]）、そうでなければ1頭（[PRIMARY]のみ）。
 */
enum class FoalPosition {
    /** 様式の表「産駒あり」欄の産駒。 */
    PRIMARY,

    /** 様式の裏「双子の繁殖成績」欄の、双子のもう一頭。 */
    TWIN,
}

/**
 * 産駒が生まれた繁殖成績と、その中での枠。
 *
 * 産駒（[BloodHorse]）から自分が生まれた繁殖成績を辿る逆リンクで、同じ繁殖成績から登録できる頭数を数えるのに使う（ADR-0092）。
 *
 * @property breedingResultId 産駒が生まれた繁殖成績
 * @property position 繁殖成績の中での枠
 */
@ValueObject data class FoalSlot(val breedingResultId: BreedingResultId, val position: FoalPosition)
