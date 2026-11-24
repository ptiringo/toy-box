package com.example.api.infrastructure.studbook.horse

import com.example.api.domain.studbook.model.horse.bloodhorse.FoalPosition

/**
 * 産駒の登録枠（`FoalPosition`）を `blood_horse.bred_from_position` に写す文字列（#625 / ADR-0092）。
 *
 * enum 名を直接書くと、ドメインの名前を変えたときに DB の値が黙って変わる。[OriginType] と同じく永続化の語彙をここに固定する。 値の許容は V28 の CHECK 制約
 * `chk_blood_horse_bred_from_position` と揃える。
 */
internal object FoalPositionColumn {
    /** 様式の表「産駒あり」欄の産駒。 */
    const val PRIMARY = "PRIMARY"

    /** 様式の裏「双子の繁殖成績」欄の、双子のもう一頭。 */
    const val TWIN = "TWIN"
}

/** ドメインの登録枠を列の値へ写す。 */
internal fun FoalPosition.toColumn(): String =
    when (this) {
        FoalPosition.PRIMARY -> FoalPositionColumn.PRIMARY
        FoalPosition.TWIN -> FoalPositionColumn.TWIN
    }

/** 列の値をドメインの登録枠へ戻す。未知の値は壊れ行として例外にする（infrastructure 層は送出してよい）。 */
internal fun String.toFoalPosition(): FoalPosition =
    when (this) {
        FoalPositionColumn.PRIMARY -> FoalPosition.PRIMARY
        FoalPositionColumn.TWIN -> FoalPosition.TWIN
        else -> error("未知の bred_from_position です: $this")
    }
