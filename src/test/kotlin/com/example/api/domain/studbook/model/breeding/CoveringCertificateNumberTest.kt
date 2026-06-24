package com.example.api.domain.studbook.model.breeding

import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import org.junit.jupiter.api.Test

/** CoveringCertificateNumber 値オブジェクトのユニットテスト */
class CoveringCertificateNumberTest {
    @Test
    fun `ブランクでない値なら生成できること`() {
        val number = CoveringCertificateNumber.create("C-2024-0001").unwrap()

        assert(number.value == "C-2024-0001")
    }

    @Test
    fun `前後の空白は trim されること`() {
        val number = CoveringCertificateNumber.create("  C-2024-0001  ").unwrap()

        assert(number.value == "C-2024-0001")
    }

    @Test
    fun `空文字ならブランクエラーを返すこと`() {
        val result = CoveringCertificateNumber.create("")

        assert(result.getError() == BlankCoveringCertificateNumber)
    }

    @Test
    fun `空白のみならブランクエラーを返すこと`() {
        val result = CoveringCertificateNumber.create("   ")

        assert(result.getError() == BlankCoveringCertificateNumber)
    }
}
