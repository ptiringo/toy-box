package com.example.api.infrastructure.studbook.horse

import com.example.api.domain.studbook.model.horse.bloodhorse.BreedType
import com.example.api.domain.studbook.model.horse.bloodhorse.CoatColor
import com.example.api.domain.studbook.model.horse.bloodhorse.ForeignSire
import com.example.api.domain.studbook.model.horse.bloodhorse.ForeignSireName
import com.example.api.domain.studbook.model.horse.bloodhorse.OriginCountry
import com.example.api.infrastructure.shared.orThrow
import java.util.UUID

/**
 * 外国種牡馬の 4 列（馬名・登録国・品種・毛色）から [ForeignSire] を復元する。
 *
 * 持込馬の出自（`studbook.blood_horse.foreign_sire_*`）と
 * 海外種付（`studbook.breeding_result.covering_foreign_sire_*`）が同じ形の列を持つため、復元をここに一本化する。4 列が揃うことは各テーブルの
 * CHECK 制約が強制しており、欠落は DB の破損として 診断メッセージつきで落とす（どの行のどの列かを残す）。
 *
 * @param id 診断メッセージに出す行の ID
 */
internal fun foreignSireOf(
    name: String?,
    studBookCountry: String?,
    breedType: String?,
    coatColor: String?,
    id: UUID,
): ForeignSire {
    val n = checkNotNull(name) { "外国種牡馬の馬名が欠落: id=$id" }
    val c = checkNotNull(studBookCountry) { "外国種牡馬の登録国が欠落: id=$id" }
    val b = checkNotNull(breedType) { "外国種牡馬の品種が欠落: id=$id" }
    val coat = checkNotNull(coatColor) { "外国種牡馬の毛色が欠落: id=$id" }
    return ForeignSire(
        name = ForeignSireName.create(n).orThrow(),
        studBookCountry = OriginCountry.create(c).orThrow(),
        breedType = BreedType.valueOf(b),
        coatColor = CoatColor.valueOf(coat),
    )
}
