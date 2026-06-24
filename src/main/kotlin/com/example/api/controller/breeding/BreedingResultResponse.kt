package com.example.api.controller.breeding

import com.example.api.domain.studbook.model.breeding.BreedingResult
import java.time.LocalDate
import java.util.UUID

/**
 * 繁殖成績リソースの表現（HTTP 契約）。
 *
 * 繁殖成績に対する操作（種付記録の Create、分娩結果報告の `:reportFoaling` カスタムメソッド）は、
 * [AIP-133](https://google.aip.dev/133) / [AIP-136](https://google.aip.dev/136) に倣い一律で
 * このリソース表現全体を返す。繁殖年は集計・報告の単位で、種付した年は種付日の年と一致する。種付せず（その年に 種付しなかった）の年次成績では種付関連の項目（[stallionId] /
 * [coveringDate] / [certificateNumber]）が null に なり、[outcome] は種付せず区分を持つ。種付した年の分娩結果は未報告なら [outcome]
 * が null。
 *
 * @property id 繁殖成績の生 UUID
 * @property breedingRegistrationId 紐づく繁殖登録（繁殖牝馬のロール）の生 UUID
 * @property breedingYear 繁殖年
 * @property stallionId 種牡馬の生 UUID。種付せずの年は null
 * @property coveringDate 種付日。種付せずの年は null
 * @property coveringPlace 種付が行われた場所。種付せずの年は null
 * @property certificateNumber 種付証明書番号。種付せずの年は null
 * @property outcome 分娩結果。種付した年で未報告なら null
 */
data class BreedingResultResponse(
    val id: UUID,
    val breedingRegistrationId: UUID,
    val breedingYear: Int,
    val stallionId: UUID?,
    val coveringDate: LocalDate?,
    val coveringPlace: String?,
    val certificateNumber: String?,
    val outcome: FoalingOutcomeResponse?,
)

/** [BreedingResult] を繁殖成績リソースの表現へ変換する。各操作の成功レスポンスはこのリソース表現を一律で返す。 */
fun BreedingResult.toResponse(): BreedingResultResponse =
    BreedingResultResponse(
        id = id.value,
        breedingRegistrationId = breedingRegistrationId.value,
        breedingYear = breedingYear.value,
        stallionId = covering?.stallionId?.value,
        coveringDate = covering?.coveringDate,
        coveringPlace = covering?.coveringPlace?.value,
        certificateNumber = covering?.certificateNumber?.value,
        outcome = outcome?.toResponse(),
    )
