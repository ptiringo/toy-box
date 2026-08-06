package com.example.api.infrastructure.iam.world

import com.example.api.application.iam.world.WorldQueries
import com.example.api.domain.iam.model.account.Account
import com.example.api.domain.iam.model.account.AccountRepository
import com.example.api.domain.iam.model.world.World
import com.example.api.domain.iam.model.world.WorldName
import com.example.api.domain.iam.model.world.WorldRepository
import com.example.api.domain.shared.AccountId
import com.example.api.support.PostgresContainerSupport
import com.github.michaelbull.result.getOrThrow
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/** [JdbcWorldRepository] と [JdbcWorldQueries] が契約を満たすことを実 DB で検証する。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class JdbcWorldRepositoryContractTest : PostgresContainerSupport() {

    @Autowired private lateinit var accounts: AccountRepository
    @Autowired private lateinit var worlds: WorldRepository
    @Autowired private lateinit var queries: WorldQueries

    /** 世界は account への FK を持つため、先にアカウントを作らないと insert できない。 */
    private fun newOwner(subjectId: String): AccountId =
        accounts
            .save(Account.create(subjectId).getOrThrow { AssertionError(it.toString()) })
            .getOrThrow { AssertionError(it.toString()) }
            .id

    private fun saveWorld(ownerId: AccountId, name: String): World =
        worlds
            .save(World.create(ownerId, name).getOrThrow { AssertionError(it.toString()) })
            .getOrThrow { AssertionError(it.toString()) }

    @Test
    fun `保存した世界を ID で引き当てられる`() {
        val ownerId = newOwner("sub-world-roundtrip")
        val saved = saveWorld(ownerId, "はじまりの牧場")

        val found = worlds.findById(saved.id)

        assert(found?.id == saved.id)
        assert(found?.accountId == ownerId)
        assert(found?.name == WorldName("はじまりの牧場"))
    }

    @Test
    fun `未登録の世界IDを引くと null`() {
        val ownerId = newOwner("sub-world-missing")
        val saved = saveWorld(ownerId, "実在する世界")
        worlds.deleteById(saved.id)

        assert(worlds.findById(saved.id) == null)
    }

    @Test
    fun `世界を持たないアカウントは所持なしと判定される`() {
        val ownerId = newOwner("sub-no-world")

        assert(!worlds.existsByAccountId(ownerId))
    }

    @Test
    fun `世界を持つアカウントは所持ありと判定される`() {
        val ownerId = newOwner("sub-has-world")
        saveWorld(ownerId, "唯一の世界")

        assert(worlds.existsByAccountId(ownerId))
    }

    @Test
    fun `改名した世界を保存できる`() {
        val ownerId = newOwner("sub-world-rename")
        val saved = saveWorld(ownerId, "旧名")

        val renamed = saved.rename("新名").getOrThrow { AssertionError(it.toString()) }
        worlds.save(renamed).getOrThrow { AssertionError(it.toString()) }

        assert(worlds.findById(saved.id)?.name == WorldName("新名"))
    }

    @Test
    fun `読み取りポートは自分の世界だけを返す`() {
        val mine = newOwner("sub-mine")
        val others = newOwner("sub-others")
        saveWorld(mine, "自分の世界A")
        saveWorld(mine, "自分の世界B")
        saveWorld(others, "他人の世界")

        val views = queries.findAllByAccountId(mine)

        assert(views.size == 2)
        assert(views.map { it.name }.toSet() == setOf("自分の世界A", "自分の世界B"))
    }
}
