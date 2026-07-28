package com.example.api.domain.studbook.model.horse.bloodhorse

import org.junit.jupiter.api.Test

/**
 * [CoatColor] の親子整合（JAIRS 登録規程実施基準 第9条第1項(2)）のユニットテスト。
 *
 * ア = 芦毛以外の父母の間に生まれた馬が芦毛（[CoatColor.violatesGrayRule]） イ =
 * 栗毛（栃栗毛を含む）の父母の間に生まれた馬が栗毛以外（[CoatColor.violatesChestnutRule]）
 */
class CoatColorTest {
    @Test
    fun `芦毛以外の父母から生まれた仔が芦毛だと芦毛規則に該当すること`() {
        assert(CoatColor.GRAY.violatesGrayRule(CoatColor.BAY, CoatColor.CHESTNUT))
    }

    @Test
    fun `父または母が芦毛なら仔が芦毛でも芦毛規則に該当しないこと`() {
        assert(!CoatColor.GRAY.violatesGrayRule(CoatColor.GRAY, CoatColor.BAY))
        assert(!CoatColor.GRAY.violatesGrayRule(CoatColor.BAY, CoatColor.GRAY))
    }

    @Test
    fun `父または母が白毛なら仔が芦毛でも芦毛規則に該当しないこと`() {
        // 第2条第3項が芦毛の遺伝子検査の対象に白毛の父母を含めており、白毛の親から芦毛の仔が生じうる
        assert(!CoatColor.GRAY.violatesGrayRule(CoatColor.WHITE, CoatColor.BAY))
        assert(!CoatColor.GRAY.violatesGrayRule(CoatColor.BAY, CoatColor.WHITE))
    }

    @Test
    fun `仔が芦毛でなければ芦毛規則に該当しないこと`() {
        assert(!CoatColor.BAY.violatesGrayRule(CoatColor.BAY, CoatColor.DARK_BAY))
    }

    @Test
    fun `栗毛同士の父母から生まれた仔が栗毛以外だと栗毛規則に該当すること`() {
        assert(CoatColor.BAY.violatesChestnutRule(CoatColor.CHESTNUT, CoatColor.DARK_CHESTNUT))
    }

    @Test
    fun `栗毛同士の父母から生まれた仔が栃栗毛なら栗毛規則に該当しないこと`() {
        // 第9条(2)イ は栃栗毛を栗毛に含める
        assert(
            !CoatColor.DARK_CHESTNUT.violatesChestnutRule(CoatColor.CHESTNUT, CoatColor.CHESTNUT)
        )
    }

    @Test
    fun `栗毛同士の父母から生まれた仔が白毛なら栗毛規則の対象外であること`() {
        // 柱書きが「白毛以外の7種の毛色に関し」と対象を限定している
        assert(!CoatColor.WHITE.violatesChestnutRule(CoatColor.CHESTNUT, CoatColor.CHESTNUT))
    }

    @Test
    fun `父母の一方が栗毛でなければ栗毛規則に該当しないこと`() {
        assert(!CoatColor.BAY.violatesChestnutRule(CoatColor.CHESTNUT, CoatColor.BAY))
        assert(!CoatColor.BAY.violatesChestnutRule(CoatColor.BAY, CoatColor.CHESTNUT))
    }
}
