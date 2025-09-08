package com.example.api.domain.horse_racing.jockey

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Jockey クラスのユニットテスト
 */
class JockeyTest {
    @Nested
    inner class ConstructorTest {
        @Test
        fun コンストラクタでプロパティが正しく設定されること() {
            val jockey = Jockey("武", "豊")
            assert(jockey.firstName == "武")
            assert(jockey.lastName == "豊")
        }
    }

    @Nested
    inner class EqualsTest {
        @Test
        fun 同じ名前でもIDが異なれば等価でない() {
            val jockey1 = Jockey(firstName = "太郎", lastName = "山田")
            val jockey2 = Jockey(firstName = "太郎", lastName = "山田")
            assert(jockey1 != jockey2)
        }

        @Test
        fun 同じインスタンスは等価である() {
            val jockey = Jockey(firstName = "次郎", lastName = "佐藤")
            assert(jockey == jockey)
        }
    }

    @Nested
    inner class HashCodeTest {
        @Test
        fun hashCodeがIDに依存していることを検証する() {
            val jockey1 = Jockey(firstName = "四郎", lastName = "田中")
            val jockey2 = Jockey(firstName = "四郎", lastName = "田中")
            assert(jockey1.hashCode() != jockey2.hashCode())
        }
    }
}

