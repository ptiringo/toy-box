package com.example.api.infrastructure.studbook.breeding

/**
 * `studbook.breeding_result.outcome_type` に書かれる判別子の文字列。
 *
 * sealed な分娩結果 [com.example.api.domain.studbook.model.breeding.FoalingOutcome] をフラット化した判別子列の
 * 値で、書き込み側（[JdbcBreedingResultRepository] の集約 ⇔ Row マッピング）と読み取り側 （[JdbcBreedingResultQueries] の
 * View 直組み・[JdbcBreedingResultSummaryQueries] の集計）が同じ語彙を使う。
 * どれか一つが生リテラルを持つと、判別子のリネームが他を無言で壊すため、出所をこの 1 箇所に集約する。 値は `FoalingOutcome` の各バリアント名に対応し、未報告は列自体が
 * NULL（この語彙には現れない）。
 */
internal object OutcomeType {
    /** 生産（産駒あり）。唯一 `outcome_foaling_date` を伴う。 */
    const val LIVE_FOAL = "LIVE_FOAL"

    /** 不受胎。 */
    const val NOT_CONCEIVED = "NOT_CONCEIVED"

    /** 流産。 */
    const val ABORTION = "ABORTION"

    /** 双子流産。 */
    const val TWIN_ABORTION = "TWIN_ABORTION"

    /** 死産。 */
    const val STILLBIRTH = "STILLBIRTH"

    /** 双子死産。 */
    const val TWIN_STILLBIRTH = "TWIN_STILLBIRTH"

    /** 生後直死。 */
    const val NEONATAL_DEATH = "NEONATAL_DEATH"

    /** 双子生後直死。 */
    const val TWIN_NEONATAL_DEATH = "TWIN_NEONATAL_DEATH"

    /** 種付せず（その年に種付しなかった）。種付列を伴わない唯一の区分。 */
    const val NOT_COVERED = "NOT_COVERED"
}
