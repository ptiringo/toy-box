package com.example.api.domain.horseracing.model.horse.bloodhorse

import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import org.junit.jupiter.api.Test

/** OriginCountry VO の不変条件のテスト */
class OriginCountryTest {
    @Test
    fun `ブランクでない原産国名から生成できること`() {
        val originCountry = OriginCountry.create("アイルランド").unwrap()

        assert(originCountry.name == "アイルランド")
    }

    @Test
    fun `前後の空白は trim されること`() {
        val originCountry = OriginCountry.create("  アメリカ  ").unwrap()

        assert(originCountry.name == "アメリカ")
    }

    @Test
    fun `空文字のとき BlankOriginCountry を返すこと`() {
        val result = OriginCountry.create("")

        assert(result.getError() == BlankOriginCountry)
    }

    @Test
    fun `空白のみのとき BlankOriginCountry を返すこと`() {
        val result = OriginCountry.create("   ")

        assert(result.getError() == BlankOriginCountry)
    }
}
