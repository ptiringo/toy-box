package com.example.api.domain.iam.model.world

import com.example.api.domain.shared.AccountId
import com.example.api.domain.shared.generateId
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.getOrThrow
import org.junit.jupiter.api.Test

class WorldTest {

    private val ownerId = AccountId(generateId())

    @Test
    fun `所有者と名前を与えると世界を生成できる`() {
        val world = World.create(ownerId, "はじまりの牧場").getOrThrow { AssertionError(it.toString()) }

        assert(world.accountId == ownerId)
        assert(world.name == WorldName("はじまりの牧場"))
    }

    @Test
    fun `名前がブランクなら世界を生成できない`() {
        val error = World.create(ownerId, "  ").getError()

        assert(error == WorldNameValidationError.Blank)
    }

    @Test
    fun `名前が64文字を超えるなら世界を生成できない`() {
        val error = World.create(ownerId, "あ".repeat(65)).getError()

        assert(error == WorldNameValidationError.TooLong)
    }

    @Test
    fun `名前がちょうど64文字なら世界を生成できる`() {
        val world =
            World.create(ownerId, "あ".repeat(64)).getOrThrow { AssertionError(it.toString()) }

        assert(world.name.value.length == 64)
    }

    @Test
    fun `改名すると新しい名前を持つ世界が返る`() {
        val world = World.create(ownerId, "旧名").getOrThrow { AssertionError(it.toString()) }

        val renamed = world.rename("新名").getOrThrow { AssertionError(it.toString()) }

        assert(renamed.name == WorldName("新名"))
        assert(renamed.id == world.id)
        assert(world.name == WorldName("旧名"))
    }

    @Test
    fun `改名でもブランクは拒否する`() {
        val world = World.create(ownerId, "旧名").getOrThrow { AssertionError(it.toString()) }

        val error = world.rename("").getError()

        assert(error == WorldNameValidationError.Blank)
    }

    @Test
    fun `所有者本人であれば所有と判定する`() {
        val world = World.create(ownerId, "自分の世界").getOrThrow { AssertionError(it.toString()) }

        assert(world.isOwnedBy(ownerId))
    }

    @Test
    fun `別のアカウントは所有者と判定しない`() {
        val world = World.create(ownerId, "自分の世界").getOrThrow { AssertionError(it.toString()) }

        assert(!world.isOwnedBy(AccountId(generateId())))
    }
}
