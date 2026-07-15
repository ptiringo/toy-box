package com.example.api.application.racing.jockey

import com.example.api.domain.racing.model.RacingPermissions
import com.example.api.domain.racing.model.jockey.Jockey
import com.example.api.domain.racing.model.jockey.JockeyRepository
import com.example.api.domain.racing.model.jockey.JockeyValidationError
import com.example.api.domain.shared.AccountId
import com.example.api.domain.shared.Actor
import com.example.api.domain.shared.Command
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class JockeyRegistrationUseCaseTest {

    private val actor =
        Actor(AccountId(UUID.randomUUID()), setOf(RacingPermissions.JOCKEY_REGISTER))

    private fun command(firstName: String, lastName: String): Command<RegisterJockeyCommand> =
        Command(RegisterJockeyCommand(firstName, lastName), Instant.now())

    @Nested
    inner class SuccessCase {
        @Test
        fun `名と姓が正しく既存ジョッキーと衝突しないとき登録に成功する`() {
            val repository = mockk<JockeyRepository>()
            every { repository.findByFullName("武", "豊") } returns null
            every { repository.save(any()) } answers { firstArg() }
            val useCase = JockeyRegistrationUseCase(repository)

            val jockey = useCase(actor, command("武", "豊")).unwrap()

            assert(jockey.firstName == "武")
            assert(jockey.lastName == "豊")
            verify(exactly = 1) { repository.save(any()) }
        }
    }

    @Nested
    inner class FailureCase {
        @Test
        fun `名がブランクのとき InvalidJockey(BlankFirstName) を返し永続化されない`() {
            val repository = mockk<JockeyRepository>()
            val useCase = JockeyRegistrationUseCase(repository)

            val result = useCase(actor, command("", "豊"))

            assert(
                result.getError() ==
                    JockeyRegistrationError.InvalidJockey(JockeyValidationError.BlankFirstName)
            )
            verify(exactly = 0) { repository.save(any()) }
        }

        @Test
        fun `姓がブランクのとき InvalidJockey(BlankLastName) を返し永続化されない`() {
            val repository = mockk<JockeyRepository>()
            val useCase = JockeyRegistrationUseCase(repository)

            val result = useCase(actor, command("武", ""))

            assert(
                result.getError() ==
                    JockeyRegistrationError.InvalidJockey(JockeyValidationError.BlankLastName)
            )
            verify(exactly = 0) { repository.save(any()) }
        }

        @Test
        fun `同姓同名のジョッキーが既に存在するとき DuplicateJockey を返し永続化されない`() {
            val existing = Jockey.create("武", "豊").unwrap()
            val repository = mockk<JockeyRepository>()
            every { repository.findByFullName("武", "豊") } returns existing
            val useCase = JockeyRegistrationUseCase(repository)

            val result = useCase(actor, command("武", "豊"))

            assert(result.getError() == JockeyRegistrationError.DuplicateJockey(existing.id))
            verify(exactly = 0) { repository.save(any()) }
        }

        @Test
        fun `権限を持たない Actor で呼ぶと Forbidden を返し引き当ても永続化もしない`() {
            val repository = mockk<JockeyRepository>()
            val useCase = JockeyRegistrationUseCase(repository)
            val noPermissionActor = Actor(AccountId(UUID.randomUUID()), emptySet())

            val result = useCase(noPermissionActor, command("武", "豊"))

            assert(
                result.getError() ==
                    JockeyRegistrationError.Forbidden(RacingPermissions.JOCKEY_REGISTER)
            )
            verify(exactly = 0) { repository.findByFullName(any(), any()) }
            verify(exactly = 0) { repository.save(any()) }
        }
    }
}
