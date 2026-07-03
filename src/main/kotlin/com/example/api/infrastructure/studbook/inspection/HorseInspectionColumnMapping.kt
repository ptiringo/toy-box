package com.example.api.infrastructure.studbook.inspection

import com.example.api.domain.studbook.model.inspection.DnaParentageResult
import com.example.api.domain.studbook.model.inspection.IdentificationFeatures
import com.example.api.domain.studbook.model.inspection.ParentageDetermination

/*
 * horse_inspection テーブルの列表現とドメイン VO の相互変換（#312 / #484）。
 *
 * 判別子フラット化（sealed ParentageDetermination ⇔ parentage_type + dna_parentage_result）と
 * 特徴記述子のフラット化（nullable IdentificationFeatures ⇔ feature_* 列）は、write 経路
 * （JdbcHorseInspectionRepository の Row マッピング）と read 経路（JdbcHorseInspectionQueries の
 * View 直組み。ADR-0031 の L2）の双方で必要になるため、パッケージ内共有のトップレベル関数に置く。
 */

internal const val PARENTAGE_BY_DNA = "BY_DNA"
internal const val PARENTAGE_BY_BLOOD_TYPE = "BY_BLOOD_TYPE"
internal const val PARENTAGE_BY_OVERSEAS_INSTITUTION = "BY_OVERSEAS_INSTITUTION"
internal const val PARENTAGE_NOT_APPLICABLE = "NOT_APPLICABLE"

/** 判別子と DNA 結果の列値から sealed [ParentageDetermination] を復元する。未知の判別子は復元データ破損として扱う。 */
internal fun toParentageDetermination(
    parentageType: String,
    dnaParentageResult: String?,
): ParentageDetermination =
    when (parentageType) {
        PARENTAGE_BY_DNA ->
            ParentageDetermination.ByDna(
                DnaParentageResult.valueOf(
                    checkNotNull(dnaParentageResult) {
                        "DNA 判定結果が欠落しています: parentage_type=$parentageType"
                    }
                )
            )
        PARENTAGE_BY_BLOOD_TYPE -> ParentageDetermination.ByBloodType
        PARENTAGE_BY_OVERSEAS_INSTITUTION -> ParentageDetermination.ByOverseasInstitution
        PARENTAGE_NOT_APPLICABLE -> ParentageDetermination.NotApplicable
        else -> error("未知の parentage_type です: $parentageType")
    }

/** feature_* 列から nullable な [IdentificationFeatures] を復元する（全 NULL なら未記録＝null）。 */
internal fun toIdentificationFeatures(
    hairWhorl: String?,
    whiteMarkings: String?,
    nosePrint: String?,
): IdentificationFeatures? =
    if (hairWhorl == null && whiteMarkings == null && nosePrint == null) {
        null
    } else {
        IdentificationFeatures(
            hairWhorl = hairWhorl,
            whiteMarkings = whiteMarkings,
            nosePrint = nosePrint,
        )
    }

/** sealed [ParentageDetermination] を判別子と DNA 結果のペアへ写す。 */
internal fun ParentageDetermination.toTypeAndResult(): Pair<String, String?> =
    when (this) {
        is ParentageDetermination.ByDna -> PARENTAGE_BY_DNA to result.name
        ParentageDetermination.ByBloodType -> PARENTAGE_BY_BLOOD_TYPE to null
        ParentageDetermination.ByOverseasInstitution -> PARENTAGE_BY_OVERSEAS_INSTITUTION to null
        ParentageDetermination.NotApplicable -> PARENTAGE_NOT_APPLICABLE to null
    }
