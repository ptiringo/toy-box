package com.example.api.controller.breeding

import com.example.api.domain.studbook.model.breeding.FoalingOutcome
import java.time.LocalDate

/*
 * 分娩結果（[FoalingOutcome]）の HTTP 契約で用いる区分 enum と、ドメインとの相互変換。
 *
 * ドメインの [FoalingOutcome] は sealed interface（生産＝[FoalingOutcome.LiveFoal] と産駒なしの各 data object）で
 * 表すが、これをそのまま wire に晒すと、ドメイン側の構造変更が HTTP 契約（および生成クライアント）を無言で破壊する。
 * これを避けるため adapter 層に契約専用の区分 enum [FoalingOutcomeDto] を置き、網羅 when で domain と往復させる
 * （区分の増減を compile エラーで検知できる）。生産（[FoalingOutcome.LiveFoal]）のみ分娩日を伴うため、wire 形は
 * 区分 + 任意の分娩日（[FoalingOutcomeResponse]）で表す。
 */

/** 分娩結果の区分（HTTP 契約）。 */
enum class FoalingOutcomeDto {
    /** 生産（産駒あり）。分娩日を伴う。 */
    LIVE_FOAL,

    /** 不受胎。 */
    NOT_CONCEIVED,

    /** 流産。 */
    ABORTION,

    /** 双子流産。 */
    TWIN_ABORTION,

    /** 死産。 */
    STILLBIRTH,

    /** 双子死産。 */
    TWIN_STILLBIRTH,

    /** 生後直死。 */
    NEONATAL_DEATH,

    /** 双子生後直死。 */
    TWIN_NEONATAL_DEATH,

    /** 種付せず（その年に種付しなかった）。種付を伴わない年次成績の区分。 */
    NOT_COVERED,
}

/**
 * 分娩結果のリソース表現（HTTP 契約）。
 *
 * @property kind 分娩結果の区分
 * @property foalingDate 分娩日。生産（[FoalingOutcomeDto.LIVE_FOAL]）のときのみ非 null
 */
data class FoalingOutcomeResponse(val kind: FoalingOutcomeDto, val foalingDate: LocalDate?)

/** ドメインの分娩結果を HTTP 契約のリソース表現へ変換する。 */
fun FoalingOutcome.toResponse(): FoalingOutcomeResponse =
    when (this) {
        is FoalingOutcome.LiveFoal ->
            FoalingOutcomeResponse(FoalingOutcomeDto.LIVE_FOAL, foalingDate)
        FoalingOutcome.NotConceived -> FoalingOutcomeResponse(FoalingOutcomeDto.NOT_CONCEIVED, null)
        FoalingOutcome.Abortion -> FoalingOutcomeResponse(FoalingOutcomeDto.ABORTION, null)
        FoalingOutcome.TwinAbortion -> FoalingOutcomeResponse(FoalingOutcomeDto.TWIN_ABORTION, null)
        FoalingOutcome.Stillbirth -> FoalingOutcomeResponse(FoalingOutcomeDto.STILLBIRTH, null)
        FoalingOutcome.TwinStillbirth ->
            FoalingOutcomeResponse(FoalingOutcomeDto.TWIN_STILLBIRTH, null)
        FoalingOutcome.NeonatalDeath ->
            FoalingOutcomeResponse(FoalingOutcomeDto.NEONATAL_DEATH, null)
        FoalingOutcome.TwinNeonatalDeath ->
            FoalingOutcomeResponse(FoalingOutcomeDto.TWIN_NEONATAL_DEATH, null)
        FoalingOutcome.NotCovered -> FoalingOutcomeResponse(FoalingOutcomeDto.NOT_COVERED, null)
    }
