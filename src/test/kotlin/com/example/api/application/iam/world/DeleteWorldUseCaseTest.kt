package com.example.api.application.iam.world

import com.example.api.domain.iam.model.world.WorldFixture
import com.example.api.domain.iam.model.world.WorldRepository
import com.example.api.domain.shared.AccountId
import com.example.api.domain.shared.Command
import com.example.api.domain.shared.generateId
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.getOrThrow
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.jupiter.api.Test

class DeleteWorldUseCaseTest {

    private val worlds = mockk<WorldRepository>(relaxed = true)
    private val clock = Clock.fixed(Instant.parse("2026-08-05T00:00:00Z"), ZoneOffset.UTC)
    private val useCase = DeleteWorldUseCase(worlds)
    private val ownerId = AccountId(generateId())

    @Test
    fun `自分の世界は削除できる`() {
        val world = WorldFixture.world(accountId = ownerId, version = 1L)
        every { worlds.findOwnedBy(ownerId, world.id) } returns world

        useCase(ownerId, Command.now(DeleteWorldCommand(world.id), clock)).getOrThrow {
            AssertionError(it.toString())
        }

        verify(exactly = 1) { worlds.deleteById(world.id) }
    }

    @Test
    fun `他人の世界は削除できず NotFound を返す`() {
        val world = WorldFixture.world(accountId = AccountId(generateId()), version = 1L)
        every { worlds.findOwnedBy(ownerId, world.id) } returns null

        val error = useCase(ownerId, Command.now(DeleteWorldCommand(world.id), clock)).getError()

        assert(error is WorldMutationError.NotFound)
        verify(exactly = 0) { worlds.deleteById(any()) }
    }
}
