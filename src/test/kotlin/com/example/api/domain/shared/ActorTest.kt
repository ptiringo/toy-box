package com.example.api.domain.shared

import java.util.UUID
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ActorTest {

    private val accountId = AccountId(UUID.fromString("00000000-0000-7000-8000-000000000001"))
    private val name = Permission("studbook:horse:name")
    private val register = Permission("studbook:horse:register")

    @Nested
    inner class SuccessCase {
        @Test
        fun `保持している権限を問われたとき can は true を返す`() {
            val actor = Actor(accountId, setOf(name, register))

            assert(actor.can(name))
        }
    }

    @Nested
    inner class FailureCase {
        @Test
        fun `保持していない権限を問われたとき can は false を返す`() {
            val actor = Actor(accountId, setOf(register))

            assert(!actor.can(name))
        }

        @Test
        fun `権限を一つも持たない Actor は何も can できない`() {
            val actor = Actor(accountId, emptySet())

            assert(!actor.can(name))
            assert(!actor.can(register))
        }
    }
}
