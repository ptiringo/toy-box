package com.example.api.controller.horse

import com.example.api.domain.studbook.model.horse.bloodhorse.Origin
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate
import java.util.UUID

/**
 * 軽種馬リソースの「出自」の表現（HTTP 契約）。
 *
 * 内国産（父母 ID あり）／輸入（原産国・揚陸日あり）／移行取り込み（判別子以外の属性を持たない）の相互排他を、判別子 `type` を持つ discriminated union として表す。
 * リソース全体は単一表現（[ADR-0008](../../docs/adr/0008-uniform-resource-representation-response.md)）を維持し、
 * 共通項は平置きのまま、相互排他な部分（出自）だけを入れ子オブジェクト `origin` の `oneOf` にする（ADR-0020）。 ドメインの sealed [Origin]
 * と表裏一体だが、wire 契約として独立させ [toApi] で往復する（[ADR-0007] と整合）。
 *
 * Jackson は [JsonTypeInfo] により `type`（`DOMESTIC` / `IMPORTED` / `CARRIED_OVER`）を出力し、springdoc は
 * [Schema] の `oneOf` ＋ `discriminatorProperty` で polymorphic スキーマを生成する。
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = OriginDto.Domestic::class, name = "DOMESTIC"),
    JsonSubTypes.Type(value = OriginDto.Imported::class, name = "IMPORTED"),
    JsonSubTypes.Type(value = OriginDto.CarriedOver::class, name = "CARRIED_OVER"),
)
@Schema(
    description = "軽種馬の出自（内国産=DOMESTIC / 輸入=IMPORTED / 移行取り込み=CARRIED_OVER の discriminated union）",
    oneOf = [OriginDto.Domestic::class, OriginDto.Imported::class, OriginDto.CarriedOver::class],
    discriminatorProperty = "type",
)
sealed interface OriginDto {
    /**
     * 内国産馬の出自。父・母の生 UUID を持つ。
     *
     * @property sireId 父（雄）の生 UUID
     * @property damId 母（雌）の生 UUID
     */
    @Schema(description = "内国産馬の出自")
    data class Domestic(val sireId: UUID, val damId: UUID) : OriginDto

    /**
     * 輸入馬・基礎輸入馬の出自。原産国名と揚陸日を持つ。
     *
     * @property country 原産国名
     * @property landingDate 揚陸日
     */
    @Schema(description = "輸入馬・基礎輸入馬の出自")
    data class Imported(val country: String, val landingDate: LocalDate) : OriginDto

    /** 移行取り込み（先行原簿に血統記録済み・当システム外）の出自。判別子以外の属性を持たない。 */
    @Schema(description = "移行取り込みの出自") data object CarriedOver : OriginDto
}

/** ドメインの [Origin] を HTTP 契約の [OriginDto] へ変換する。 */
fun Origin.toApi(): OriginDto =
    when (this) {
        is Origin.Domestic -> OriginDto.Domestic(sireId = sireId.value, damId = damId.value)
        is Origin.Imported ->
            OriginDto.Imported(country = originCountry.name, landingDate = landingDate.value)
        Origin.CarriedOver -> OriginDto.CarriedOver
    }
