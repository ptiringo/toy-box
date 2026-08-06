package com.example.api.application.iam.world

import com.example.api.domain.iam.model.world.World
import com.example.api.domain.iam.model.world.WorldFixture
import com.example.api.domain.iam.model.world.WorldRepository
import com.example.api.domain.shared.AccountId
import com.example.api.domain.shared.Command
import com.example.api.domain.shared.generateId
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.getOrThrow
import io.mockk.every
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.jupiter.api.Test

class RenameWorldUseCaseTest {

    private val worlds = mockk<WorldRepository>()
    private val clock = Clock.fixed(Instant.parse("2026-08-05T00:00:00Z"), ZoneOffset.UTC)
    private val useCase = RenameWorldUseCase(worlds)
    private val ownerId = AccountId(generateId())

    @Test
    fun `自分の世界は改名できる`() {
        val world = WorldFixture.world(accountId = ownerId, name = "旧名", version = 1L)
        every { worlds.findById(world.id) } returns world
        every { worlds.save(any()) } answers { Ok(firstArg<World>()) }

        val view =
            useCase(ownerId, Command.now(RenameWorldCommand(world.id.value, "新名"), clock))
                .getOrThrow { AssertionError(it.toString()) }

        assert(view.name == "新名")
    }

    @Test
    fun `他人の世界は存在しないものとして扱う`() {
        val world =
            WorldFixture.world(accountId = AccountId(generateId()), name = "他人の世界", version = 1L)
        every { worlds.findById(world.id) } returns world

        val error =
            useCase(ownerId, Command.now(RenameWorldCommand(world.id.value, "乗っ取り"), clock))
                .getError()

        assert(error is WorldMutationError.NotFound)
    }

    @Test
    fun `存在しない世界の改名は NotFound`() {
        val missingId = generateId()
        every { worlds.findById(any()) } returns null

        val error =
            useCase(ownerId, Command.now(RenameWorldCommand(missingId, "新名"), clock)).getError()

        assert(error is WorldMutationError.NotFound)
    }

    @Test
    fun `改名後の名前がブランクなら失敗する`() {
        val world = WorldFixture.world(accountId = ownerId, name = "旧名", version = 1L)
        every { worlds.findById(world.id) } returns world

        val error =
            useCase(ownerId, Command.now(RenameWorldCommand(world.id.value, ""), clock)).getError()

        assert(error is WorldMutationError.InvalidName)
    }
}
