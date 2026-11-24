package com.example.api.domain.studbook.service.horse

import com.example.api.domain.studbook.model.breeding.BreedingResultId
import com.example.api.domain.studbook.model.breeding.FoalingOutcome
import com.example.api.domain.studbook.model.horse.bloodhorse.FoalPosition
import com.example.api.domain.studbook.model.horse.bloodhorse.FoalSlot

/**
 * 生産の繁殖成績から次に登録する産駒の枠を割り当てる（#625 / ADR-0092）。
 *
 * 1つの繁殖成績から血統登録できる産駒は、双子なら2頭（[FoalPosition.PRIMARY]→[FoalPosition.TWIN]の順）。
 * 双子でなければ1頭（[FoalPosition.PRIMARY]のみ）。 登録済みの枠（[takenPositions]）は呼び出し側が永続化ポートから読んで渡す。
 *
 * @param breedingResultId 産駒が生まれる繁殖成績
 * @param liveFoal その繁殖成績の分娩結果（生産）
 * @param takenPositions その繁殖成績から登録済みの産駒が占めている枠
 * @return 割り当てた枠。上限（[FoalingOutcome.LiveFoal.foalLimit]）に達していれば null
 */
fun allocateFoalSlot(
    breedingResultId: BreedingResultId,
    liveFoal: FoalingOutcome.LiveFoal,
    takenPositions: Set<FoalPosition>,
): FoalSlot? {
    val candidates =
        if (liveFoal.twin == null) listOf(FoalPosition.PRIMARY)
        else listOf(FoalPosition.PRIMARY, FoalPosition.TWIN)
    return candidates.firstOrNull { it !in takenPositions }?.let { FoalSlot(breedingResultId, it) }
}

/**
 * 割り当てた枠の産駒の生年月日（[FoalPosition.TWIN] ならもう一頭の分娩日、それ以外は分娩日）。
 *
 * enum の `when` は合成クラス（`$WhenMappings`）を生み、service パッケージの「トップレベル関数のみ」規約に触れるため `if` で書く。
 */
internal fun FoalingOutcome.LiveFoal.foalingDateOf(position: FoalPosition) =
    if (position == FoalPosition.TWIN) {
        checkNotNull(twin) { "双子でない生産に TWIN 枠は割り当てない" }.foalingDate
    } else {
        foalingDate
    }
