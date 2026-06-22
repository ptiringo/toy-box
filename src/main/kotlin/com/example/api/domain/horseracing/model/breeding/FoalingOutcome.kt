package com.example.api.domain.horseracing.model.breeding

import java.time.LocalDate
import org.jmolecules.ddd.annotation.ValueObject

/**
 * 年次の繁殖成績区分。繁殖牝馬がその年に迎えた帰結を表す。
 *
 * 「繁殖成績報告書」（様式第14号）が報告する年次の繁殖成績を sealed なドメイン語彙として表す。生産（産駒あり） の [LiveFoal]
 * と、産駒なしに終わった各区分（様式第14号裏「子馬のない母の繁殖成績」の8区分）に分かれる。区分は相互排他であり、`when` の網羅性で漏れを防ぐため sealed interface
 * とする。
 *
 * 8区分のうち7区分（[NotConceived] / [Abortion] / [TwinAbortion] / [Stillbirth] / [TwinStillbirth] /
 * [NeonatalDeath] / [TwinNeonatalDeath]）は**種付後に生じる**帰結だが、8区分目の [NotCovered]（種付せず）だけは
 * 種付（[Covering]）が存在しない年次成績を表す。このため [NotCovered] は [BreedingResult] において covering を 伴わない唯一の区分であり、整合は
 * [BreedingResult] のファクトリと不変条件が強制する（経緯は ADR-0016）。妊娠期間・ 生後日数の閾値（例: 流産と死産の境、生後直死の判定日数）は様式の OCR
 * が不鮮明なため数値としては保持せず、報告者の 区分判断をそのまま受け取る分類として扱う。
 */
sealed interface FoalingOutcome {
    /**
     * 生産（産駒あり）。分娩により生存産駒を得た帰結。
     *
     * ここで生じた産駒は、母（繁殖登録証明書）と父（[Covering] の種付証明書）を添えて血統登録 （registerInStudBook）される入力に接続する。産駒は血統登録の成立まで
     * [com.example.api.domain.horseracing.model.horse.bloodhorse.BloodHorse] として実体化しない
     * ため、本帰結は分娩日のみを保持する。
     *
     * @property foalingDate 分娩日（産駒の出生年月日）
     */
    @ValueObject data class LiveFoal(val foalingDate: LocalDate) : FoalingOutcome

    /** 不受胎。種付したが受胎が確認できなかった。 */
    @ValueObject data object NotConceived : FoalingOutcome

    /** 流産。妊娠期間の途中で胎子が死亡または死んで娩出された。 */
    @ValueObject data object Abortion : FoalingOutcome

    /** 双子流産。双子の流産。 */
    @ValueObject data object TwinAbortion : FoalingOutcome

    /** 死産。妊娠後期に胎子が死亡または死んで娩出された。 */
    @ValueObject data object Stillbirth : FoalingOutcome

    /** 双子死産。双子の死産。 */
    @ValueObject data object TwinStillbirth : FoalingOutcome

    /** 生後直死。出生直後（ごく短期間）に死亡した。 */
    @ValueObject data object NeonatalDeath : FoalingOutcome

    /** 双子生後直死。双子の生後直死。 */
    @ValueObject data object TwinNeonatalDeath : FoalingOutcome

    /**
     * 種付せず。その年に種付しなかった（種付が存在しない）年次成績。
     *
     * 様式第14号裏「子馬のない母の繁殖成績」の8区分目。他の7区分が種付を起点とするのに対し、本区分は種付 （[Covering]）を伴わない。[BreedingResult]
     * ではこの区分のみ covering を持たず、生成は専用ファクトリ [BreedingResult.createUncovered] に限る。
     */
    @ValueObject data object NotCovered : FoalingOutcome
}
