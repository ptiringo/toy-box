package com.example.api.domain.horseracing.model.horse.bloodhorse

import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import org.junit.jupiter.api.Test

/** HorseName 値オブジェクトの不変条件（カタカナ2〜9文字）のユニットテスト。 */
class HorseNameTest {
    @Test
    fun `カタカナ2〜9文字なら生成でき trim される`() {
        val name = HorseName.create("  オグリキャップ  ").unwrap()

        assert(name.value == "オグリキャップ")
    }

    @Test
    fun `長音符を含むカタカナも許可される`() {
        val name = HorseName.create("サウンドトゥルー").unwrap()

        assert(name.value == "サウンドトゥルー")
    }

    @Test
    fun `下限の2文字は許可される`() {
        assert(HorseName.create("アア").unwrap().value == "アア")
    }

    @Test
    fun `上限の9文字は許可される`() {
        assert(HorseName.create("ディープインパクト").unwrap().value == "ディープインパクト")
    }

    @Test
    fun `1文字は短すぎて InvalidHorseName を返す`() {
        assert(HorseName.create("ア").getError() == InvalidHorseName)
    }

    @Test
    fun `10文字は長すぎて InvalidHorseName を返す`() {
        assert(HorseName.create("アイウエオカキクケコ").getError() == InvalidHorseName)
    }

    @Test
    fun `ひらがなは使用できず InvalidHorseName を返す`() {
        assert(HorseName.create("おぐり").getError() == InvalidHorseName)
    }

    @Test
    fun `半角カナは使用できず InvalidHorseName を返す`() {
        assert(HorseName.create("ｵｸﾞﾘ").getError() == InvalidHorseName)
    }

    @Test
    fun `空文字は InvalidHorseName を返す`() {
        assert(HorseName.create("").getError() == InvalidHorseName)
    }
}
