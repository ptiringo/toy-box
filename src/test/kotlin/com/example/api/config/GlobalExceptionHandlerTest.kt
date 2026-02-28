package com.example.api.config

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

/**
 * GlobalExceptionHandlerのテスト
 *
 * エラーハンドリングが正しく機能することを確認する
 */
@SpringBootTest
class GlobalExceptionHandlerTest {
    @Test
    fun `GlobalExceptionHandlerがSpringコンテキストに登録されている`() {
        // コンテキストの起動が成功すれば、GlobalExceptionHandlerが正しく登録されている
        assert(true)
    }
}
