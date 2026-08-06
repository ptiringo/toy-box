package com.example.api.application.iam.me

import com.example.api.domain.iam.model.account.Account
import com.example.api.domain.iam.model.account.AccountFixture
import com.example.api.domain.iam.model.account.AccountRepository
import com.example.api.domain.iam.model.account.SubjectId
import com.example.api.domain.iam.model.world.World
import com.example.api.domain.iam.model.world.WorldRepository
import com.example.api.domain.shared.Command
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.getOrThrow
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.jupiter.api.Test

class ProvisionMeUseCaseTest {

    private val accounts = mockk<AccountRepository>()
    private val worlds = mockk<WorldRepository>(relaxed = true)
    private val clock = Clock.fixed(Instant.parse("2026-08-05T00:00:00Z"), ZoneOffset.UTC)
    private val useCase = ProvisionMeUseCase(accounts, worlds)

    private fun command(subjectId: String) = Command.now(ProvisionMeCommand(subjectId), clock)

    @Test
    fun `未登録の subject ならアカウントと最初の世界を作る`() {
        val savedAccounts = mutableListOf<Account>()
        every { accounts.findBySubjectId(SubjectId("sub-new")) } returns null
        every { accounts.save(capture(savedAccounts)) } answers { Ok(firstArg<Account>()) }
        every { worlds.existsByAccountId(any()) } returns false
        every { worlds.save(any()) } answers { Ok(firstArg<World>()) }

        val accountId = useCase(command("sub-new")).getOrThrow { AssertionError(it.toString()) }

        verify(exactly = 1) { accounts.save(any()) }
        verify(exactly = 1) { worlds.save(any()) }
        assert(accountId == savedAccounts.single().id)
    }

    @Test
    fun `登録済みの subject ならアカウントを作り直さない`() {
        val existing = AccountFixture.account(subjectId = "sub-existing", version = 1L)
        every { accounts.findBySubjectId(SubjectId("sub-existing")) } returns existing
        every { worlds.existsByAccountId(existing.id) } returns true

        val accountId =
            useCase(command("sub-existing")).getOrThrow { AssertionError(it.toString()) }

        assert(accountId == existing.id)
        verify(exactly = 0) { accounts.save(any()) }
    }

    @Test
    fun `世界を既に持っていれば追加で作らない`() {
        val existing = AccountFixture.account(subjectId = "sub-has-world", version = 1L)
        every { accounts.findBySubjectId(SubjectId("sub-has-world")) } returns existing
        every { worlds.existsByAccountId(existing.id) } returns true

        useCase(command("sub-has-world")).getOrThrow { AssertionError(it.toString()) }

        verify(exactly = 0) { worlds.save(any()) }
    }

    @Test
    fun `登録済みだが世界を持たなければ最初の世界を作る`() {
        val existing = AccountFixture.account(subjectId = "sub-lost-worlds", version = 1L)
        every { accounts.findBySubjectId(SubjectId("sub-lost-worlds")) } returns existing
        every { worlds.existsByAccountId(existing.id) } returns false
        every { worlds.save(any()) } answers { Ok(firstArg<World>()) }

        useCase(command("sub-lost-worlds")).getOrThrow { AssertionError(it.toString()) }

        verify(exactly = 1) { worlds.save(any()) }
    }

    @Test
    fun `subject がブランクなら失敗する`() {
        every { accounts.findBySubjectId(SubjectId("  ")) } returns null

        val error = useCase(command("  ")).getError()

        assert(error is ProvisionMeError.InvalidSubject)
    }
}
