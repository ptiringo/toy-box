package com.example.api.controller.breeding

import com.example.api.application.studbook.breeding.BreedingResultSummaryView
import java.math.BigDecimal
import java.util.UUID

/**
 * 繁殖成績の年次集計リソースの表現（JAIRS 様式第2号の1行に対応）。
 *
 * 読み取りモデル [BreedingResultSummaryView] を素直に写すフラット DTO。JSON は snake_case で公開される （`spring.jackson` の
 * naming strategy）。ドメイン enum を契約に持たないため Wire enum は不要。
 *
 * @property conceptionRate 受胎率(%) 小数1桁、[productionRate] 生産率(%) 小数1桁
 */
data class BreedingResultSummaryResponse(
    val stallionId: UUID,
    val breedingYear: Int,
    val maresCovered: Int,
    val conceived: Int,
    val liveFoals: Int,
    val conceptionRate: BigDecimal,
    val productionRate: BigDecimal,
)

/** 読み取りモデルをリソース表現へ写す。 */
fun BreedingResultSummaryView.toResponse(): BreedingResultSummaryResponse =
    BreedingResultSummaryResponse(
        stallionId = stallionId,
        breedingYear = breedingYear,
        maresCovered = maresCovered,
        conceived = conceived,
        liveFoals = liveFoals,
        conceptionRate = conceptionRate,
        productionRate = productionRate,
    )
