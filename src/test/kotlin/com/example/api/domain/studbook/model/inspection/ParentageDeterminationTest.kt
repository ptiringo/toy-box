package com.example.api.domain.studbook.model.inspection

import org.junit.jupiter.api.Test

/** [ParentageDetermination.confirmsDeclaredParents] のユニットテスト */
class ParentageDeterminationTest {
    @Test
    fun `DNA 判定が矛盾なしなら申告父母を確認済みとみなす`() {
        val determination = ParentageDetermination.ByDna(DnaParentageResult.CONSISTENT)

        assert(determination.confirmsDeclaredParents())
    }

    @Test
    fun `DNA 判定が矛盾なし以外なら確認できていないとみなす`() {
        val inconsistent = ParentageDetermination.ByDna(DnaParentageResult.INCONSISTENT)
        val untested = ParentageDetermination.ByDna(DnaParentageResult.UNTESTED)
        assert(!inconsistent.confirmsDeclaredParents())
        assert(!untested.confirmsDeclaredParents())
    }

    @Test
    fun `血液型・海外機関フォールバックは本スライスでは確認済みとして扱う`() {
        assert(ParentageDetermination.ByBloodType.confirmsDeclaredParents())
        assert(ParentageDetermination.ByOverseasInstitution.confirmsDeclaredParents())
    }

    @Test
    fun `親子判定の対象外は確認できていないとみなす`() {
        assert(!ParentageDetermination.NotApplicable.confirmsDeclaredParents())
    }
}
