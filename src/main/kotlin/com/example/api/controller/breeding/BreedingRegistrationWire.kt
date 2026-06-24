package com.example.api.controller.breeding

import com.example.api.domain.studbook.model.breeding.BreedingRole
import com.example.api.domain.studbook.model.breeding.RetirementReason

/*
 * 繁殖登録（[com.example.api.domain.studbook.model.breeding.BreedingRegistration]）の HTTP 契約で用いる区分 enum
 * と、ドメインとの相互変換。
 *
 * ドメインの enum（[BreedingRole] / [RetirementReason]）をそのまま wire に晒すと、ドメイン側の区分変更が HTTP 契約
 * （および生成クライアント）を無言で破壊する。これを避けるため adapter 層に契約専用の区分 enum を置き、網羅 when で
 * domain と往復させる（区分の増減を compile エラーで検知できる）。ADR-0007。
 */

/** 繁殖登録によって付与されるロールの区分（HTTP 契約）。 */
enum class BreedingRoleDto {
    /** 種牡馬（繁殖の用に供する雄馬）。 */
    STALLION,

    /** 繁殖牝馬（繁殖の用に供する雌馬）。 */
    BROODMARE,
}

/** ドメインの繁殖ロールを HTTP 契約の区分へ変換する。 */
fun BreedingRole.toDto(): BreedingRoleDto =
    when (this) {
        BreedingRole.STALLION -> BreedingRoleDto.STALLION
        BreedingRole.BROODMARE -> BreedingRoleDto.BROODMARE
    }

/** 供用停止の事由の区分（HTTP 契約）。 */
enum class RetirementReasonDto {
    /** 死亡（死産・生後直死を除く）。 */
    DEATH,

    /** 用途変更（繁殖以外の用途への変更）。 */
    USE_CHANGE,

    /** その他の事由。 */
    OTHER,
}

/** ドメインの供用停止事由を HTTP 契約の区分へ変換する。 */
fun RetirementReason.toDto(): RetirementReasonDto =
    when (this) {
        RetirementReason.DEATH -> RetirementReasonDto.DEATH
        RetirementReason.USE_CHANGE -> RetirementReasonDto.USE_CHANGE
        RetirementReason.OTHER -> RetirementReasonDto.OTHER
    }
