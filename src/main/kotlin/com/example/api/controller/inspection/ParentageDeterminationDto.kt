package com.example.api.controller.inspection

import com.example.api.controller.horse.DnaParentageResultDto
import com.example.api.controller.horse.toApi
import com.example.api.controller.horse.toDomain
import com.example.api.domain.studbook.model.inspection.ParentageDetermination
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import io.swagger.v3.oas.annotations.media.Schema

/**
 * 審査リソースの「親子判定」の表現（HTTP 契約）。
 *
 * DNA 型（判定結果あり）／血液型・海外機関フォールバック／対象外の相互排他を、判別子 `type` を持つ discriminated union
 * として表す（ADR-0020、[com.example.api.controller.horse.OriginDto] と同方針）。ドメインの sealed
 * [ParentageDetermination] と表裏一体だが、wire 契約として独立させ [toApi] / [toDomain] で往復する （ADR-0007 と整合）。DNA
 * 判定結果の enum は軽種馬リソースと同一概念のため [DnaParentageResultDto] を共有する（契約が分岐したら分割する）。
 *
 * Jackson は [JsonTypeInfo] により `type` を出力し、springdoc は [Schema] の `oneOf` ＋ `discriminatorProperty`
 * で polymorphic スキーマを生成する。
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = ParentageDeterminationDto.ByDna::class, name = "BY_DNA"),
    JsonSubTypes.Type(value = ParentageDeterminationDto.ByBloodType::class, name = "BY_BLOOD_TYPE"),
    JsonSubTypes.Type(
        value = ParentageDeterminationDto.ByOverseasInstitution::class,
        name = "BY_OVERSEAS_INSTITUTION",
    ),
    JsonSubTypes.Type(
        value = ParentageDeterminationDto.NotApplicable::class,
        name = "NOT_APPLICABLE",
    ),
)
@Schema(
    description =
        "親子判定（DNA=BY_DNA / 血液型=BY_BLOOD_TYPE / 海外機関=BY_OVERSEAS_INSTITUTION / " +
            "対象外=NOT_APPLICABLE の discriminated union）",
    oneOf =
        [
            ParentageDeterminationDto.ByDna::class,
            ParentageDeterminationDto.ByBloodType::class,
            ParentageDeterminationDto.ByOverseasInstitution::class,
            ParentageDeterminationDto.NotApplicable::class,
        ],
    discriminatorProperty = "type",
)
sealed interface ParentageDeterminationDto {
    /**
     * DNA 型による親子判定。
     *
     * @property dnaParentageResult DNA 型による判定結果
     */
    @Schema(description = "DNA 型による親子判定")
    data class ByDna(val dnaParentageResult: DnaParentageResultDto) : ParentageDeterminationDto

    /** 血液型検査によるフォールバック。追加フィールドなし（詳細は #267）。 */
    @Schema(description = "血液型検査によるフォールバック判定。追加フィールドなし（詳細は #267）")
    data object ByBloodType : ParentageDeterminationDto

    /** 承認海外機関の判定によるフォールバック。追加フィールドなし（詳細は #267）。 */
    @Schema(description = "承認海外機関の判定によるフォールバック判定。追加フィールドなし（詳細は #267）")
    data object ByOverseasInstitution : ParentageDeterminationDto

    /** 親子判定の対象外（父母不明等）。追加フィールドなし。 */
    @Schema(description = "親子判定の対象外（父母不明等）。追加フィールドなし")
    data object NotApplicable : ParentageDeterminationDto
}

/** HTTP 契約の親子判定をドメインの親子判定へ変換する。 */
fun ParentageDeterminationDto.toDomain(): ParentageDetermination =
    when (this) {
        is ParentageDeterminationDto.ByDna ->
            ParentageDetermination.ByDna(dnaParentageResult.toDomain())
        ParentageDeterminationDto.ByBloodType -> ParentageDetermination.ByBloodType
        ParentageDeterminationDto.ByOverseasInstitution ->
            ParentageDetermination.ByOverseasInstitution
        ParentageDeterminationDto.NotApplicable -> ParentageDetermination.NotApplicable
    }

/** ドメインの親子判定を HTTP 契約の親子判定へ変換する。 */
fun ParentageDetermination.toApi(): ParentageDeterminationDto =
    when (this) {
        is ParentageDetermination.ByDna -> ParentageDeterminationDto.ByDna(result.toApi())
        ParentageDetermination.ByBloodType -> ParentageDeterminationDto.ByBloodType
        ParentageDetermination.ByOverseasInstitution ->
            ParentageDeterminationDto.ByOverseasInstitution
        ParentageDetermination.NotApplicable -> ParentageDeterminationDto.NotApplicable
    }
