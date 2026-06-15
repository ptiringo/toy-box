package com.example.api.domain.horseracing.model.horse.bloodhorse

import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import org.junit.jupiter.api.Test

/** MicrochipNumber の検証ロジックのユニットテスト */
class MicrochipNumberTest {
    @Test
    fun `15桁のASCII数字なら生成できること`() {
        val number = MicrochipNumber.create("392140000000001").unwrap()
        assert(number.value == "392140000000001")
    }

    @Test
    fun `前後の空白はトリムされること`() {
        val number = MicrochipNumber.create("  392140000000001  ").unwrap()
        assert(number.value == "392140000000001")
    }

    @Test
    fun `15桁でも全角数字は弾かれること`() {
        // Char.isDigit() は全角・アラビア数字も true にするため、ASCII 限定であることを担保する
        val result = MicrochipNumber.create("３９２１４０００００００００１")
        assert(result.getError() == InvalidMicrochipNumber)
    }

    @Test
    fun `桁数が足りないと弾かれること`() {
        val result = MicrochipNumber.create("123")
        assert(result.getError() == InvalidMicrochipNumber)
    }
}
