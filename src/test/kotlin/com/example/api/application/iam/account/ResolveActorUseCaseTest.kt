package com.example.api.application.iam.account

import com.example.api.domain.iam.model.account.Account
import com.example.api.domain.iam.model.account.AccountRepository
import com.example.api.domain.iam.model.account.Role
import com.example.api.domain.iam.model.account.SubjectId
import com.example.api.domain.shared.Command
import com.example.api.domain.studbook.model.StudbookPermissions
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ResolveActorUseCaseTest {

    private fun command(subjectId: String): Command<ResolveActorCommand> =
        Command(ResolveActorCommand(subjectId), Instant.now())

    @Nested
    inner class SuccessCase {
        @Test
        fun `登録済みの subject から役割を展開した Actor を組み立てる`() {
            val account = Account.create(SubjectId("idp-sub-001"), setOf(Role.BREEDER)).unwrap()
            val repository =
                mockk<AccountRepository> {
                    every { findBySubjectId(SubjectId("idp-sub-001")) } returns account
                    every { findPermissionsOf(setOf(Role.BREEDER)) } returns
                        setOf(StudbookPermissions.BREEDING_RESULT_REPORT_FOALING)
                }
            val useCase = ResolveActorUseCase(repository)

            val actor = useCase(command("idp-sub-001")).unwrap()

            assert(actor.accountId == account.id)
            assert(actor.can(StudbookPermissions.BREEDING_RESULT_REPORT_FOALING))
            assert(!actor.can(StudbookPermissions.HORSE_REGISTER))
        }
    }

    @Nested
    inner class FailureCase {
        @Test
        fun `IdP には居るが account が未登録の subject は AccountNotFound を返す`() {
            val repository =
                mockk<AccountRepository> {
                    every { findBySubjectId(SubjectId("unknown-sub")) } returns null
                }
            val useCase = ResolveActorUseCase(repository)

            val result = useCase(command("unknown-sub"))

            assert(result.getError() == ResolveActorUseCaseError.AccountNotFound("unknown-sub"))
        }
    }
}
