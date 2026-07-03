package com.example.api.domain.studbook.model.breeding

import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.Year
import java.time.ZoneId
import org.jmolecules.ddd.annotation.ValueObject

/**
 * 繁殖成績報告書（様式第14号）の提出期限。
 *
 * 登録規程第25条は、種雌馬の繁殖成績報告書を「毎年5月31日までに」提出することを求める。報告対象は前年の種付 （とその帰結の分娩）なので、繁殖年 Y の成績の提出期限は翌年 Y+1 の
 * 5/31 となる（[of]）。期限は日本の暦日で 締まるため、提出日時（[Instant]）は [REPORTING_ZONE]（Asia/Tokyo）の暦日に写してから照合する
 * （[submissionDateOf]。与えられた時刻の純粋な写像であり、現在時刻の取得はドメインでは行わない）。
 *
 * 期限超過の提出は拒否しない（第25条ただし書き＝成績確定後の速やかな提出義務があり、受理される）。超過か どうかは [isMissedBy]
 * で判定し、ドメインの事実として記録する（#455。原典確認は Issue コメント参照）。
 */
@ValueObject
@JvmInline
value class BreedingReportDeadline private constructor(val date: LocalDate) {

    /** [submittedOn]（提出日）が期限を過ぎているかを返す。期限日（5/31）当日は期限内。 */
    fun isMissedBy(submittedOn: LocalDate): Boolean = submittedOn.isAfter(date)

    companion object {
        /** 提出期限の暦日が属するタイムゾーン（JAIRS への提出は日本の暦日で締まる）。 */
        val REPORTING_ZONE: ZoneId = ZoneId.of("Asia/Tokyo")

        /** 繁殖年 [breedingYear] の報告書の提出期限（翌年 5/31）を返す純粋関数。 */
        @Suppress("MagicNumber")
        fun of(breedingYear: Year): BreedingReportDeadline =
            BreedingReportDeadline(LocalDate.of(breedingYear.value + 1, Month.MAY, 31))

        /** 提出日時 [issuedAt] を [REPORTING_ZONE] の暦日（提出日）に写す純粋関数。 */
        fun submissionDateOf(issuedAt: Instant): LocalDate =
            issuedAt.atZone(REPORTING_ZONE).toLocalDate()
    }
}
