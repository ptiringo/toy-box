package com.example.api.domain.horseracing.model.breeding

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import java.time.LocalDate
import org.jmolecules.ddd.annotation.ValueObject

/**
 * 有効期間の不変条件違反（終点が起点より前）。
 *
 * @property start 指定された起点
 * @property end 指定された終点
 */
data class InvalidValidityPeriod(val start: LocalDate, val end: LocalDate)

/**
 * 有効期間（起点・終点とも含む閉区間）。
 *
 * 種畜証明書（[StudCertificate]）の有効期間を表す。種付が産駒の血統登録要件を満たすには、その種付日が証明書の有効期間内で
 * ある必要がある（登録規程実施基準・第9条第1項(1)）。期間は暦日で扱えば足りるため [LocalDate] の閉区間とし、起点・終点の 当日を含む。
 *
 * @property start 有効期間の起点（当日を含む）
 * @property end 有効期間の終点（当日を含む）
 */
@ValueObject
@ConsistentCopyVisibility
data class ValidityPeriod private constructor(val start: LocalDate, val end: LocalDate) {
    /** [date] が有効期間内（起点・終点の当日を含む）かを返す。 */
    fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)

    companion object {
        /** 終点が起点以降であることを検証して [ValidityPeriod] を生成する。 */
        fun create(
            start: LocalDate,
            end: LocalDate,
        ): Result<ValidityPeriod, InvalidValidityPeriod> =
            if (end.isBefore(start)) Err(InvalidValidityPeriod(start, end))
            else Ok(ValidityPeriod(start, end))
    }
}
