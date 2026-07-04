package com.example.api.domain.studbook.model.breeding

import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.Year
import java.time.ZoneId
import org.jmolecules.ddd.annotation.ValueObject

/**
 * 種付成績報告書（様式第13号）の提出期限。
 *
 * 登録規程第25条(1)は、種雄馬の種付成績報告書を「毎年9月30日までに」提出することを求める。種付シーズンは 当年内に完結するため、種付年 Y の成績の提出期限は当年 Y の 9/30
 * となる（[of]。牝側の繁殖成績報告書 [BreedingReportDeadline]＝翌年5/31 と非対称）。期限は日本の暦日で締まるため、提出日時（[Instant]）は
 * [REPORTING_ZONE]（Asia/Tokyo）の暦日に写してから照合する（[submissionDateOf]。与えられた時刻の純粋な
 * 写像であり、現在時刻の取得はドメインでは行わない）。
 *
 * 期限超過の提出は拒否しない（第25条の帰結は第29条2項の裁量サンクションであり提出時の効果ではない）。 超過かどうかは [isMissedBy]
 * で判定し、ドメインの事実として記録する（#540。牝側 #455 と同じ方針）。
 *
 * [REPORTING_ZONE] と [submissionDateOf] は姉妹 VO [BreedingReportDeadline] と重複するが、様式ごとの 期限 VO
 * を自己完結に保つ意図的な重複（3 例目が現れたら共通化する）。
 */
@ValueObject
@JvmInline
value class CoveringReportDeadline private constructor(val date: LocalDate) {

    /** [submittedOn]（提出日）が期限を過ぎているかを返す。期限日（9/30）当日は期限内。 */
    fun isMissedBy(submittedOn: LocalDate): Boolean = submittedOn.isAfter(date)

    companion object {
        /** 提出期限の暦日が属するタイムゾーン（JAIRS への提出は日本の暦日で締まる）。 */
        val REPORTING_ZONE: ZoneId = ZoneId.of("Asia/Tokyo")

        /** 種付年 [coveringYear] の報告書の提出期限（当年 9/30）を返す純粋関数。 */
        @Suppress("MagicNumber")
        fun of(coveringYear: Year): CoveringReportDeadline =
            CoveringReportDeadline(LocalDate.of(coveringYear.value, Month.SEPTEMBER, 30))

        /** 提出日時 [issuedAt] を [REPORTING_ZONE] の暦日（提出日）に写す純粋関数。 */
        fun submissionDateOf(issuedAt: Instant): LocalDate =
            issuedAt.atZone(REPORTING_ZONE).toLocalDate()
    }
}
