package com.example.api.domain.horseracing.model.horse.bloodhorse

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import org.jmolecules.ddd.annotation.ValueObject

/** 生産者名がブランク。 */
data object BlankBreeder

/**
 * 生産者。
 *
 * 軽種馬を生産した牧場・個人。本モデルでは生産者を独立した集約とはせず、血統登録に記録される生産者名のみを値オブジェクトとして保持する （生産者を集約として扱う必要が出た時点で
 * [BloodHorseId] と同様に ID 参照へ切り出す）。
 *
 * @property name 生産者名
 */
@ValueObject
@JvmInline
value class Breeder private constructor(val name: String) {
    companion object {
        /** ブランクでないことを検証して [Breeder] を生成する。 */
        fun create(name: String): Result<Breeder, BlankBreeder> =
            if (name.isBlank()) {
                Err(BlankBreeder)
            } else {
                Ok(Breeder(name.trim()))
            }
    }
}
