package com.example.api.domain.iam.model.account

import com.example.api.domain.shared.Permission
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class AccountTest {

    @Nested
    inner class SuccessCase {
        @Test
        fun `subject と役割を与えるとアカウントが作られ ID が採番される`() {
            val account = Account.create(SubjectId("idp-sub-001"), setOf(Role.REGISTRAR)).unwrap()

            assert(account.subjectId == SubjectId("idp-sub-001"))
            assert(account.roles == setOf(Role.REGISTRAR))
            assert(account.version == null)
        }

        @Test
        fun `役割から展開した権限を渡すと自分を Actor に写せる`() {
            val account = Account.create(SubjectId("idp-sub-001"), setOf(Role.BREEDER)).unwrap()
            val permissions = setOf(Permission("studbook:breedingResult:reportFoaling"))

            val actor = account.toActor(permissions)

            assert(actor.accountId == account.id)
            assert(actor.can(Permission("studbook:breedingResult:reportFoaling")))
        }
    }

    @Nested
    inner class FailureCase {
        @Test
        fun `subject が空白のとき BlankSubjectId を返す`() {
            val result = Account.create(SubjectId(" "), setOf(Role.VIEWER))

            assert(result.getError() == AccountValidationError.BlankSubjectId)
        }

        @Test
        fun `役割が一つも無いとき NoRoles を返す`() {
            val result = Account.create(SubjectId("idp-sub-001"), emptySet())

            assert(result.getError() == AccountValidationError.NoRoles)
        }
    }
}
