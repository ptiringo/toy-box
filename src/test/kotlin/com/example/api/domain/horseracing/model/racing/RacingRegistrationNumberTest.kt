package com.example.api.domain.horseracing.model.racing

import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import org.junit.jupiter.api.Test

/** RacingRegistrationNumber の不変条件のユニットテスト */
class RacingRegistrationNumberTest {
    @Test
    fun `ブランクでない値は競走馬登録番号を生成できること`() {
        val number = RacingRegistrationNumber.create("R2024001").unwrap()
        assert(number.value == "R2024001")
    }

    @Test
    fun `前後の空白はトリムされること`() {
        val number = RacingRegistrationNumber.create("  R2024001  ").unwrap()
        assert(number.value == "R2024001")
    }

    @Test
    fun `空文字は BlankRacingRegistrationNumber を返すこと`() {
        val result = RacingRegistrationNumber.create("")
        assert(result.getError() == BlankRacingRegistrationNumber)
    }

    @Test
    fun `空白のみは BlankRacingRegistrationNumber を返すこと`() {
        val result = RacingRegistrationNumber.create("   ")
        assert(result.getError() == BlankRacingRegistrationNumber)
    }
}
