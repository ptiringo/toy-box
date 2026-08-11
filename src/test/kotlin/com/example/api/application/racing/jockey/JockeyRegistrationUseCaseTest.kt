package com.example.api.application.racing.jockey

import com.example.api.application.shared.idempotency.IdempotencyRecord
import com.example.api.application.shared.idempotency.IdempotencyStore
import com.example.api.domain.racing.model.jockey.Jockey
import com.example.api.domain.racing.model.jockey.JockeyId
import com.example.api.domain.racing.model.jockey.JockeyRepository
import com.example.api.domain.racing.model.jockey.JockeyValidationError
import com.example.api.domain.shared.AccountId
import com.example.api.domain.shared.Actor
import com.example.api.domain.shared.Command
import com.example.api.domain.shared.Idempotency
import com.example.api.domain.shared.WorldId
import com.example.api.domain.shared.generateId
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/** 世界スコープ（#704）のテスト用フィクスチャ。ネストしたテストクラスからも参照できるようファイル直下に置く。 */
private val worldId = WorldId(generateId())
private val actor = Actor(accountId = AccountId(generateId()), worldId = worldId)

class JockeyRegistrationUseCaseTest {
    private fun command(
        firstName: String,
        lastName: String,
        idempotency: Idempotency? = null,
    ): Command<RegisterJockeyCommand> =
        Command(RegisterJockeyCommand(firstName, lastName), Instant.now(), idempotency)

    @Nested
    inner class SuccessCase {
        @Test
        fun `名と姓が正しく既存ジョッキーと衝突しないとき登録に成功する`() {
            val repository = mockk<JockeyRepository>()
            every { repository.findByFullName(worldId, "武", "豊") } returns null
            every { repository.save(worldId, any()) } answers { secondArg() }
            val useCase = JockeyRegistrationUseCase(repository, mockk<IdempotencyStore>())

            val jockey = useCase(actor, command("武", "豊")).unwrap()

            assert(jockey.firstName == "武")
            assert(jockey.lastName == "豊")
            verify(exactly = 1) { repository.save(worldId, any()) }
        }
    }

    @Nested
    inner class FailureCase {
        @Test
        fun `名がブランクのとき InvalidJockey(BlankFirstName) を返し永続化されない`() {
            val repository = mockk<JockeyRepository>()
            val useCase = JockeyRegistrationUseCase(repository, mockk<IdempotencyStore>())

            val result = useCase(actor, command("", "豊"))

            assert(
                result.getError() ==
                    JockeyRegistrationError.InvalidJockey(JockeyValidationError.BlankFirstName)
            )
            verify(exactly = 0) { repository.save(worldId, any()) }
        }

        @Test
        fun `姓がブランクのとき InvalidJockey(BlankLastName) を返し永続化されない`() {
            val repository = mockk<JockeyRepository>()
            val useCase = JockeyRegistrationUseCase(repository, mockk<IdempotencyStore>())

            val result = useCase(actor, command("武", ""))

            assert(
                result.getError() ==
                    JockeyRegistrationError.InvalidJockey(JockeyValidationError.BlankLastName)
            )
            verify(exactly = 0) { repository.save(worldId, any()) }
        }

        @Test
        fun `同姓同名のジョッキーが既に存在するとき DuplicateJockey を返し永続化されない`() {
            val existing = Jockey.create("武", "豊").unwrap()
            val repository = mockk<JockeyRepository>()
            every { repository.findByFullName(worldId, "武", "豊") } returns existing
            val useCase = JockeyRegistrationUseCase(repository, mockk<IdempotencyStore>())

            val result = useCase(actor, command("武", "豊"))

            assert(result.getError() == JockeyRegistrationError.DuplicateJockey(existing.id))
            verify(exactly = 0) { repository.save(worldId, any()) }
        }
    }

    @Nested
    inner class IdempotencyCase {
        private val repository = mockk<JockeyRepository>()
        private val store = mockk<IdempotencyStore>()
        private val useCase = JockeyRegistrationUseCase(repository, store)

        @Test
        fun `冪等キーが無ければ記録を触らない`() {
            every { repository.findByFullName(worldId, "武", "豊") } returns null
            every { repository.save(worldId, any()) } answers { secondArg() }

            useCase(actor, command("武", "豊")).unwrap()

            verify(exactly = 0) { store.claim(any(), any(), any()) }
        }

        @Test
        fun `冪等キー付きの初回は実処理を行い結果を記録する`() {
            every { store.claim(worldId, "key-first", "fp") } returns
                IdempotencyRecord(requestFingerprint = "fp", resourceId = null)
            every { repository.findByFullName(worldId, "武", "豊") } returns null
            every { repository.save(worldId, any()) } answers { secondArg() }
            every { store.recordResource(worldId, "key-first", any()) } returns Unit

            val jockey = useCase(actor, command("武", "豊", Idempotency("key-first", "fp"))).unwrap()

            verify(exactly = 1) { store.recordResource(worldId, "key-first", jockey.id.value) }
        }

        @Test
        fun `記録済みの再送は実処理をせず記録済みの集約を返す`() {
            val recorded = Jockey.create("武", "豊").unwrap()
            every { store.claim(worldId, "key-replay", "fp") } returns
                IdempotencyRecord(requestFingerprint = "fp", resourceId = recorded.id.value)
            every { repository.findById(worldId, recorded.id) } returns recorded

            val jockey = useCase(actor, command("武", "豊", Idempotency("key-replay", "fp"))).unwrap()

            assert(jockey.id == recorded.id)
            verify(exactly = 0) { repository.save(worldId, any()) }
        }

        @Test
        fun `記録済みだが集約が見つからない場合は再登録する`() {
            val missingResourceId = generateId()
            every { store.claim(worldId, "key-missing", "fp") } returns
                IdempotencyRecord(requestFingerprint = "fp", resourceId = missingResourceId)
            every { repository.findById(worldId, JockeyId(missingResourceId)) } returns null
            every { repository.findByFullName(worldId, "武", "豊") } returns null
            every { repository.save(worldId, any()) } answers { secondArg() }
            every { store.recordResource(worldId, "key-missing", any()) } returns Unit

            val jockey =
                useCase(actor, command("武", "豊", Idempotency("key-missing", "fp"))).unwrap()

            verify(exactly = 1) { repository.save(worldId, any()) }
            verify(exactly = 1) { store.recordResource(worldId, "key-missing", jockey.id.value) }
        }

        @Test
        fun `同じキーを別内容で使うと IdempotencyKeyReused を返す`() {
            every { store.claim(worldId, "key-reused", "fp-new") } returns
                IdempotencyRecord(requestFingerprint = "fp-original", resourceId = null)

            val result = useCase(actor, command("武", "豊", Idempotency("key-reused", "fp-new")))

            assert(result.getError() == JockeyRegistrationError.IdempotencyKeyReused)
            verify(exactly = 0) { repository.save(worldId, any()) }
        }
    }
}
