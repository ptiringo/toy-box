package com.example.api.controller.horse

import com.example.api.application.studbook.breeding.ForeignSireView
import com.example.api.domain.studbook.model.horse.bloodhorse.ForeignSire
import io.swagger.v3.oas.annotations.media.Schema

/**
 * 外国種牡馬の表現（HTTP 契約）。日本の繁殖登録を持たない、外国の血統登録機関に登録された種牡馬。
 *
 * 海外種付の繁殖成績（`BreedingResultResponse.foreignSire`）と、持込馬の出自（父）で共用する入れ子 DTO。 品種・毛色はドメイン enum を晒さず wire
 * enum で公開する（ADR-0007）。
 *
 * @property name 馬名（外国の登録名）
 * @property studBookCountry 登録している血統登録機関の国
 * @property breedType 品種
 * @property coatColor 毛色
 */
@Schema(description = "外国種牡馬")
data class ForeignSireResponse(
    val name: String,
    val studBookCountry: String,
    val breedType: BreedTypeDto,
    val coatColor: CoatColorDto,
)

/** [ForeignSire] を外国種牡馬の表現へ変換する。 */
fun ForeignSire.toResponse(): ForeignSireResponse =
    ForeignSireResponse(
        name = name.value,
        studBookCountry = studBookCountry.name,
        breedType = breedType.toApi(),
        coatColor = coatColor.toApi(),
    )

/** 読み取りモデル [ForeignSireView] を外国種牡馬の表現へ変換する。 */
fun ForeignSireView.toResponse(): ForeignSireResponse =
    ForeignSireResponse(
        name = name,
        studBookCountry = studBookCountry,
        breedType = breedType.toApi(),
        coatColor = coatColor.toApi(),
    )
