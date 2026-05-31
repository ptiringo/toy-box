package com.example.api.domain.horseracing.jockey

import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/** Jockey クラスのユニットテスト */
class JockeyTest {
    @Nested
    inner class CreateTest {
        @Test
        fun `正しい名前で create するとプロパティが正しく設定されること`() {
            val jockey = Jockey.create("武", "豊").unwrap()
            assert(jockey.firstName == "武")
            assert(jockey.lastName == "豊")
        }

        @Test
        fun `firstName がブランクのとき BlankFirstName を返すこと`() {
            val result = Jockey.create("", "豊")
            assert(result.getError() == JockeyValidationError.BlankFirstName)
        }

        @Test
        fun `firstName が空白文字のみのとき BlankFirstName を返すこと`() {
            val result = Jockey.create("   ", "豊")
            assert(result.getError() == JockeyValidationError.BlankFirstName)
        }

        @Test
        fun `lastName がブランクのとき BlankLastName を返すこと`() {
            val result = Jockey.create("武", "")
            assert(result.getError() == JockeyValidationError.BlankLastName)
        }
    }

    @Nested
    inner class EqualsTest {
        @Test
        fun `同じ名前でもIDが異なれば等価でない`() {
            val jockey1 = Jockey.create("太郎", "山田").unwrap()
            val jockey2 = Jockey.create("太郎", "山田").unwrap()
            assert(jockey1 != jockey2)
        }

        @Test
        fun `同じインスタンスは等価である`() {
            val jockey = Jockey.create("次郎", "佐藤").unwrap()
            assert(jockey == jockey)
        }
    }

    @Nested
    inner class HashCodeTest {
        @Test
        fun `hashCodeがIDに依存していることを検証する`() {
            val jockey1 = Jockey.create("四郎", "田中").unwrap()
            val jockey2 = Jockey.create("四郎", "田中").unwrap()
            assert(jockey1.hashCode() != jockey2.hashCode())
        }
    }
}
