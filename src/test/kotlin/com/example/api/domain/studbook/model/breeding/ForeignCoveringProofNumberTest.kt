package com.example.api.domain.studbook.model.breeding

import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import org.junit.jupiter.api.Test

/** ForeignCoveringProofNumber 値オブジェクトのユニットテスト */
class ForeignCoveringProofNumberTest {
    @Test
    fun `ブランクでない値なら生成できること`() {
        val number = ForeignCoveringProofNumber.create("IE-1999-0004").unwrap()

        assert(number.value == "IE-1999-0004")
    }

    @Test
    fun `前後の空白は trim されること`() {
        val number = ForeignCoveringProofNumber.create("  IE-1999-0004 ").unwrap()

        assert(number.value == "IE-1999-0004")
    }

    @Test
    fun `空白のみならブランクエラーを返すこと`() {
        val result = ForeignCoveringProofNumber.create("   ")

        assert(result.getError() == BlankForeignCoveringProofNumber)
    }
}
