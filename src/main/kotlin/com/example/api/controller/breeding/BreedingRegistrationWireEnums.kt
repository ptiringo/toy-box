package com.example.api.controller.breeding

import com.example.api.domain.studbook.model.breeding.BreedingRole
import com.example.api.domain.studbook.model.breeding.RetirementReason

/**
 * 繁殖登録リソースの wire enum 群。
 *
 * ドメイン enum（[BreedingRole] / [RetirementReason]）を HTTP 契約へ直接晒さず、契約専用の `〜Dto` enum を adapter 層に置いて
 * [toApi] / [toDomain] の網羅 `when` でマッピングする（ドメイン側の列挙子リネームが契約を無言で壊すのを防ぐ。ADR-0007）。
 * [RetirementReasonDto] は response と供用停止 `:retire` の request で共有するため、リソース root に置く。
 */

/** 繁殖登録によって付与されるロールの wire 表現（[BreedingRole] の契約専用 enum）。 */
enum class BreedingRoleDto {
    /** 種牡馬（繁殖の用に供する雄馬）。 */
    STALLION,

    /** 繁殖牝馬（繁殖の用に供する雌馬）。 */
    BROODMARE,
}

/** [BreedingRole] を wire 表現 [BreedingRoleDto] へマッピングする。 */
fun BreedingRole.toApi(): BreedingRoleDto =
    when (this) {
        BreedingRole.STALLION -> BreedingRoleDto.STALLION
        BreedingRole.BROODMARE -> BreedingRoleDto.BROODMARE
    }

/** 繁殖供用停止の事由の wire 表現（[RetirementReason] の契約専用 enum）。 */
enum class RetirementReasonDto {
    /** 死亡（死産・生後直死を除く）。 */
    DEATH,

    /** 用途変更（繁殖以外の用途への変更）。 */
    USE_CHANGE,

    /** その他の事由。 */
    OTHER,
}

/** [RetirementReason] を wire 表現 [RetirementReasonDto] へマッピングする。 */
fun RetirementReason.toApi(): RetirementReasonDto =
    when (this) {
        RetirementReason.DEATH -> RetirementReasonDto.DEATH
        RetirementReason.USE_CHANGE -> RetirementReasonDto.USE_CHANGE
        RetirementReason.OTHER -> RetirementReasonDto.OTHER
    }

/** wire 表現 [RetirementReasonDto] をドメインの [RetirementReason] へマッピングする（供用停止 `:retire` の入力）。 */
fun RetirementReasonDto.toDomain(): RetirementReason =
    when (this) {
        RetirementReasonDto.DEATH -> RetirementReason.DEATH
        RetirementReasonDto.USE_CHANGE -> RetirementReason.USE_CHANGE
        RetirementReasonDto.OTHER -> RetirementReason.OTHER
    }
