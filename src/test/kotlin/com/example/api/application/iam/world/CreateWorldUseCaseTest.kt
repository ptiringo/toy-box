package com.example.api.application.iam.world

import com.example.api.domain.iam.model.world.World
import com.example.api.domain.iam.model.world.WorldRepository
import com.example.api.domain.iam.model.world.WorldSaveFailure
import com.example.api.domain.shared.AccountId
import com.example.api.domain.shared.Command
import com.example.api.domain.shared.generateId
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.getOrThrow
import io.mockk.every
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.jupiter.api.Test

class CreateWorldUseCaseTest {

    private val worlds = mockk<WorldRepository>()
    private val clock = Clock.fixed(Instant.parse("2026-08-05T00:00:00Z"), ZoneOffset.UTC)
    private val useCase = CreateWorldUseCase(worlds)
    private val accountId = AccountId(generateId())

    @Test
    fun `名前を与えると世界を作れる`() {
        every { worlds.saveIfNameAvailable(any()) } answers { Ok(firstArg<World>()) }

        val view =
            useCase(accountId, Command.now(CreateWorldCommand("二つ目の牧場"), clock)).getOrThrow {
                AssertionError(it.toString())
            }

        assert(view.name == "二つ目の牧場")
    }

    @Test
    fun `名前がブランクなら作れない`() {
        val error = useCase(accountId, Command.now(CreateWorldCommand(" "), clock)).getError()

        assert(error is CreateWorldError.InvalidName)
    }

    @Test
    fun `同名の世界が既にあれば競合として返す`() {
        every { worlds.saveIfNameAvailable(any()) } returns Err(WorldSaveFailure.NameTaken)

        val error = useCase(accountId, Command.now(CreateWorldCommand("重複する名前"), clock)).getError()

        assert(error is CreateWorldError.Conflict)
    }

    @Test
    fun `保存が並行更新と競合しても競合として返す`() {
        every { worlds.saveIfNameAvailable(any()) } returns Err(WorldSaveFailure.Conflict)

        val error = useCase(accountId, Command.now(CreateWorldCommand("競合する牧場"), clock)).getError()

        assert(error is CreateWorldError.Conflict)
    }
}
