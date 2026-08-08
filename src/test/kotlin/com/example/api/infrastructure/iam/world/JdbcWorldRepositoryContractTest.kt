package com.example.api.infrastructure.iam.world

import com.example.api.application.iam.world.WorldQueries
import com.example.api.domain.iam.model.account.Account
import com.example.api.domain.iam.model.account.AccountRepository
import com.example.api.domain.iam.model.world.World
import com.example.api.domain.iam.model.world.WorldName
import com.example.api.domain.iam.model.world.WorldRepository
import com.example.api.domain.shared.AccountId
import com.example.api.domain.shared.WorldId
import com.example.api.domain.shared.generateId
import com.example.api.support.PostgresContainerSupport
import com.github.michaelbull.result.getOrThrow
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DuplicateKeyException

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
    fun `所有付き lookup で自分の世界を ID で引き当てられる`() {
        val ownerId = newOwner("sub-world-roundtrip")
        val saved = saveWorld(ownerId, "はじまりの牧場")

        val found = worlds.findOwnedBy(ownerId, saved.id)

        assert(found?.id == saved.id)
        assert(found?.accountId == ownerId)
        assert(found?.name == WorldName("はじまりの牧場"))
    }

    @Test
    fun `未登録の世界IDを所有付き lookup で引くと null`() {
        val ownerId = newOwner("sub-world-missing")
        val saved = saveWorld(ownerId, "実在する世界")
        worlds.deleteById(saved.id)

        assert(worlds.findOwnedBy(ownerId, saved.id) == null)
    }

    @Test
    fun `他人の世界を所有付き lookup で引くと null`() {
        val owner = newOwner("sub-world-owner")
        val intruder = newOwner("sub-world-intruder")
        val saved = saveWorld(owner, "他人の牧場")

        assert(worlds.findOwnedBy(intruder, saved.id) == null)
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

        assert(worlds.findOwnedBy(ownerId, saved.id)?.name == WorldName("新名"))
    }

    @Test
    fun `同名の世界は未所持と判定される`() {
        val ownerId = newOwner("sub-world-name-exists")

        assert(!worlds.existsByAccountIdAndName(ownerId, WorldName("未使用の名前")))

        saveWorld(ownerId, "使用済みの名前")

        assert(worlds.existsByAccountIdAndName(ownerId, WorldName("使用済みの名前")))
    }

    @Test
    fun `同名の世界を重複作成すると Err ではなく DuplicateKeyException が飛ぶ`() {
        // I-1: save は OptimisticLockingFailureException しか捕まえないため、UNIQUE 制約違反
        // （DuplicateKeyException）は Err(UpdateConflict) に化けず未捕捉のまま伝播する。
        // このためユースケース側は existsByAccountIdAndName による事前照会で重複を弾く必要があり、
        // save 自体は「事前照会をすり抜けた極小のレース窓」に対する backstop として未捕捉のまま残す。
        val ownerId = newOwner("sub-world-duplicate")
        saveWorld(ownerId, "重複名")
        val duplicate = World.create(ownerId, "重複名").getOrThrow { AssertionError(it.toString()) }

        val thrown = runCatching { worlds.save(duplicate) }.exceptionOrNull()

        assert(thrown is DuplicateKeyException)
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

    @Test
    fun `所有している世界は existsOwnedBy が true になる`() {
        val ownerId = newOwner("sub-exists-owned")
        val saved = saveWorld(ownerId, "所有している世界")

        assert(queries.existsOwnedBy(ownerId, saved.id))
    }

    @Test
    fun `他人の世界は existsOwnedBy が false になる`() {
        val ownerId = newOwner("sub-exists-owner")
        val otherId = newOwner("sub-exists-other")
        val saved = saveWorld(ownerId, "他人の世界")

        assert(!queries.existsOwnedBy(otherId, saved.id))
    }

    @Test
    fun `存在しない世界IDは existsOwnedBy が false になる`() {
        val ownerId = newOwner("sub-exists-missing")

        assert(!queries.existsOwnedBy(ownerId, WorldId(generateId())))
    }
}
