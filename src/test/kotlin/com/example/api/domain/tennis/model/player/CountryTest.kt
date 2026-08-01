package com.example.api.domain.tennis.model.player

import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import org.junit.jupiter.api.Test

/** Country 値オブジェクトの不変条件（ISO 3166-1 alpha-3 の形式）のユニットテスト。 */
class CountryTest {
    @Test
    fun `英字3文字なら生成できる`() {
        assert(Country.create("JPN").unwrap().code == "JPN")
    }

    @Test
    fun `小文字は大文字へ正規化される`() {
        assert(Country.create("jpn").unwrap().code == "JPN")
    }

    @Test
    fun `前後の空白は trim される`() {
        assert(Country.create("  esp  ").unwrap().code == "ESP")
    }

    @Test
    fun `2文字は InvalidCountryCode を返す`() {
        assert(Country.create("JP").getError() == InvalidCountryCode("JP"))
    }

    @Test
    fun `4文字は InvalidCountryCode を返す`() {
        assert(Country.create("JPNX").getError() == InvalidCountryCode("JPNX"))
    }

    @Test
    fun `数字混じりは InvalidCountryCode を返す`() {
        assert(Country.create("JP1").getError() == InvalidCountryCode("JP1"))
    }

    @Test
    fun `ブランクは InvalidCountryCode を返す`() {
        assert(Country.create("   ").getError() == InvalidCountryCode("   "))
    }

    @Test
    fun `エラーは正規化前の生の入力を保持する`() {
        assert(Country.create("  jp  ").getError() == InvalidCountryCode("  jp  "))
    }
}
