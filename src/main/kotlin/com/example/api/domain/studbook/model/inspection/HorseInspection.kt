package com.example.api.domain.studbook.model.inspection

import com.example.api.domain.shared.Entity
import com.example.api.domain.shared.generateId
import org.jmolecules.ddd.annotation.AggregateRoot
import org.jmolecules.ddd.annotation.Identity

/**
 * 登録に関する馬の審査を表す集約ルート（JAIRS 登録規程実施基準 第6〜7条の2）。
 *
 * 個体識別（マイクロチップ・特徴記述子）と親子判定（DNA 型／血液型／海外機関）を一体で記録する。検体・遺伝情報の 所有権が JAIRS に帰属し登録原簿に記載される＝固有同一性を持つため、
 * [com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorse]
 * の属性ではなく独立集約とする。審査は血統登録より前（原則離乳前）に行われ、登録（`BloodHorse.create`）が確定済みの
 * 本集約を消費する。本スライスでは検体採取→検査中の状態機械は持たず、確定済み審査の記録に限る（軽量ライフサイクル）。
 *
 * 状態はイミュータブルに扱う。生成は [create]、永続化復元は [reconstitute] のみに限り、コンストラクタは private とする。
 *
 * @property id 審査ID（生成時に自動採番）
 * @property microchipNumber マイクロチップ番号（ISO 11784/11785。第7条の2）
 * @property features 特徴記述子（旋毛・白斑・鼻紋）。未記録なら null
 * @property parentage 親子判定（DNA 基本／血液型・海外機関フォールバック／対象外）
 */
@AggregateRoot
class HorseInspection
private constructor(
    @field:Identity override val id: HorseInspectionId,
    val microchipNumber: MicrochipNumber,
    val features: IdentificationFeatures?,
    val parentage: ParentageDetermination,
) : Entity<HorseInspectionId>() {
    companion object {
        /** 確定済みの審査を生成する。識別子・親子判定はいずれも検証済み VO であり、本ファクトリは追加の前提条件を 持たず生成する（ID を採番するのみ）。 */
        fun create(
            microchipNumber: MicrochipNumber,
            parentage: ParentageDetermination,
            features: IdentificationFeatures? = null,
        ): HorseInspection =
            HorseInspection(
                id = HorseInspectionId(generateId()),
                microchipNumber = microchipNumber,
                features = features,
                parentage = parentage,
            )

        /** 永続化層に保存済みの状態から再構成する（検証・採番なし）。infrastructure 層の復元専用。 */
        fun reconstitute(
            id: HorseInspectionId,
            microchipNumber: MicrochipNumber,
            parentage: ParentageDetermination,
            features: IdentificationFeatures?,
        ): HorseInspection = HorseInspection(id, microchipNumber, features, parentage)
    }
}
