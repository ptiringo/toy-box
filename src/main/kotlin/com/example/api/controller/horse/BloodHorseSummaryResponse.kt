package com.example.api.controller.horse

import com.example.api.application.studbook.horse.BloodHorseView
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate
import java.util.UUID

/**
 * 軽種馬一覧の要素表現（HTTP 契約・サマリ）。
 *
 * 一覧（List）は件数が多くなりうるため、単一リソースの完全表現 [BloodHorseResponse]（マイクロチップ・出自の入れ子を含む）
 * とは別に、一覧に要る最小限の属性だけを持つ軽量サマリを返す。詳細が要るユースケースが現れたら Get（by-id）で 完全表現を返す（#495）。enum はドメイン enum を wire
 * に晒さず `〜Dto` へ写す（ADR-0007）。
 *
 * @property id 軽種馬の生 UUID
 * @property registrationNumber 血統登録番号
 * @property sex 性
 * @property coatColor 毛色
 * @property breedType 品種
 * @property dateOfBirth 生年月日
 * @property breeder 生産者名
 * @property name 馬名。未命名なら null
 */
@Schema(description = "軽種馬一覧の要素表現（サマリ）")
data class BloodHorseSummaryResponse(
    val id: UUID,
    val registrationNumber: String,
    val sex: SexDto,
    val coatColor: CoatColorDto,
    val breedType: BreedTypeDto,
    val dateOfBirth: LocalDate,
    val breeder: String,
    val name: String?,
)

/** 読み取りモデル [BloodHorseView] を軽種馬一覧のサマリ表現へ変換する。 */
fun BloodHorseView.toSummaryResponse(): BloodHorseSummaryResponse =
    BloodHorseSummaryResponse(
        id = id,
        registrationNumber = registrationNumber,
        sex = sex.toApi(),
        coatColor = coatColor.toApi(),
        breedType = breedType.toApi(),
        dateOfBirth = dateOfBirth,
        breeder = breeder,
        name = name,
    )
