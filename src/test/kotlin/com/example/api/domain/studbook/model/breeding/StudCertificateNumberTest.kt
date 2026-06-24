package com.example.api.domain.studbook.model.breeding

import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import org.junit.jupiter.api.Test

/** [StudCertificateNumber] のユニットテスト */
class StudCertificateNumberTest {
    @Test
    fun `ブランクでない番号なら生成できること`() {
        val number = StudCertificateNumber.create("S-2024-0001").unwrap()

        assert(number.value == "S-2024-0001")
    }

    @Test
    fun `前後の空白は trim されること`() {
        val number = StudCertificateNumber.create("  S-2024-0001  ").unwrap()

        assert(number.value == "S-2024-0001")
    }

    @Test
    fun `ブランクだと BlankStudCertificateNumber を返すこと`() {
        val result = StudCertificateNumber.create("   ")

        assert(result.getError() == BlankStudCertificateNumber)
    }
}
