package com.example.api.domain.studbook.model.horse.bloodhorse

import com.example.api.domain.shared.createNonBlank
import com.github.michaelbull.result.Result
import org.jmolecules.ddd.annotation.ValueObject

/** 原産国名がブランク。 */
data object BlankOriginCountry

/**
 * 原産国。
 *
 * 輸入馬・基礎輸入馬の血統登録で記録される、その馬の生まれた国。内国産馬は原産国を持たない（[BloodHorse.originCountry] は null）。
 *
 * 品種・血統の確定は承認海外機関の血統書及び輸出証明書に依拠するが、本モデルでは原産国名のみを値オブジェクトとして保持する （承認海外機関による品種確定や原産国の ISO
 * コード正規化は別途のモデリングに委ねる）。
 *
 * @property name 原産国名
 */
@ValueObject
@JvmInline
value class OriginCountry private constructor(val name: String) {
    companion object {
        /** ブランクでないことを検証して [OriginCountry] を生成する。 */
        fun create(name: String): Result<OriginCountry, BlankOriginCountry> =
            createNonBlank(name, BlankOriginCountry, ::OriginCountry)
    }
}
