package com.example.api.domain.sakamichi

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Member クラスのユニットテスト
 */
class MemberTest {

    /** equals メソッドのテスト */
    @Nested
    inner class EqualsTest {
        @Test
        fun 同じIDのMember同士は等価である() {
            val id = MemberId(1L)
            val memberA = Member(id)
            val memberB = Member(id)
            assert(memberA == memberB)
        }

        @Test
        fun 異なるIDのMember同士は等価でない() {
            val memberA = Member(MemberId(1L))
            val memberB = Member(MemberId(2L))
            assert(memberA != memberB)
        }
    }

    /** hashCode メソッドのテスト */
    @Nested inner class HashCodeTest {
        @Test
        fun MemberのhashCodeはIDに依存する() {
            val id = MemberId(10L)
            val memberA = Member(id)
            val memberB = Member(id)
            assert(memberA.hashCode() == memberB.hashCode())
        }

        @Test
        fun Member自身との比較は常に等価() {
            val member = Member(MemberId(1L))
            assert(member == member)
        }
    }
}
