package com.example.api.domain.studbook.model.inspection

import org.jmolecules.ddd.annotation.ValueObject

/**
 * 親子判定の戦略。
 *
 * DNA 型検査（[ByDna]）を基本とし、父母死亡・血液型のみ確認・輸入馬では血液型検査（[ByBloodType]）や
 * 承認海外機関の判定（[ByOverseasInstitution]）にフォールバックする。父母不明等で対象外なら [NotApplicable]。
 * 本スライスでは区分（型）のみを表し、フォールバックの詳細な判定ロジックは別途（#267）に委ねる。相互排他を sealed で型強制する（[Origin] と同方針）。
 */
@ValueObject
sealed interface ParentageDetermination {
    /** DNA 型による親子判定（基本）。 */
    @ValueObject data class ByDna(val result: DnaParentageResult) : ParentageDetermination

    /** 血液型検査によるフォールバック（父母死亡・血液型のみ確認等）。詳細は #267。 */
    @ValueObject data object ByBloodType : ParentageDetermination

    /** 承認海外機関の判定によるフォールバック（輸入馬等）。詳細は #267。 */
    @ValueObject data object ByOverseasInstitution : ParentageDetermination

    /** 親子判定の対象外（父母不明の輸入馬、先行原簿で確認済みの移行取り込みなど、当システムで判定を実施しない経路）。 */
    @ValueObject data object NotApplicable : ParentageDetermination

    /**
     * 申告された父母との親子関係が確認できているか。内国産血統登録（[BloodHorse.create]）の前提条件判定に使う。
     *
     * [ByDna] は DNA 判定が [DnaParentageResult.CONSISTENT] のときのみ確認済み。フォールバック（[ByBloodType] /
     * [ByOverseasInstitution]）は本スライスでは確認済みとして扱う（詳細条件は #267）。[NotApplicable] は当システムで判定を
     * 実施しない経路（輸入馬・移行取り込み等）のため false（内国産登録の前提を満たさない）。
     */
    fun confirmsDeclaredParents(): Boolean =
        when (this) {
            is ByDna -> result == DnaParentageResult.CONSISTENT
            ByBloodType -> true
            ByOverseasInstitution -> true
            NotApplicable -> false
        }
}
