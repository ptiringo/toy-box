package com.example.api.domain.shared

import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import org.junit.jupiter.api.Test

/** createNonBlank コンビネータのユニットテスト */
class StringValueObjectsTest {
    /** ブランク時に返すエラー型 */
    private data object Blank

    @Test
    fun `ブランクでなければtrim済みの値でファクトリが呼ばれOkを返すこと`() {
        val result = createNonBlank("  abc  ", Blank) { it }
        assert(result.unwrap() == "abc")
    }

    @Test
    fun `空文字ならファクトリを呼ばずErrを返すこと`() {
        var called = false
        val result =
            createNonBlank("", Blank) {
                called = true
                it
            }
        assert(result.getError() == Blank)
        assert(!called)
    }

    @Test
    fun `空白のみでもブランクとして弾かれること`() {
        val result = createNonBlank("   ", Blank) { it }
        assert(result.getError() == Blank)
    }
}
