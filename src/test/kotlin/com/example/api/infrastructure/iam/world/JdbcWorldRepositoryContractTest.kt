package com.example.api.infrastructure.iam.world

import com.example.api.application.iam.world.WorldQueries
import com.example.api.domain.iam.model.account.Account
import com.example.api.domain.iam.model.account.AccountRepository
import com.example.api.domain.iam.model.world.World
import com.example.api.domain.iam.model.world.WorldName
import com.example.api.domain.iam.model.world.WorldRepository
import com.example.api.domain.iam.model.world.WorldSaveFailure
import com.example.api.domain.shared.AccountId
import com.example.api.domain.shared.WorldId
import com.example.api.domain.shared.generateId
import com.example.api.support.PostgresContainerSupport
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.getOrThrow
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.IllegalTransactionStateException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

/** [JdbcWorldRepository] と [JdbcWorldQueries] が契約を満たすことを実 DB で検証する。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class JdbcWorldRepositoryContractTest : PostgresContainerSupport() {

    @Autowired private lateinit var accounts: AccountRepository
    @Autowired private lateinit var worlds: WorldRepository
    @Autowired private lateinit var queries: WorldQueries
    @Autowired private lateinit var transactionManager: PlatformTransactionManager

    /**
     * [WorldRepository.saveIfNameAvailable] は呼び出し側のトランザクションを要求する（`Propagation.MANDATORY`）。
     *
     * ロックで直列化する口なので、トランザクションが無ければ意味を成さないため。本番ではユースケースの `@Transactional` が境界になるが、契約テストは Spring
     * の宣言的境界の外側で走るので、ここで明示的に張る。
     */
    private val transaction by lazy { TransactionTemplate(transactionManager) }

    private fun <T : Any> inTransaction(block: () -> T): T =
        checkNotNull(transaction.execute { block() })

    /** 世界は account への FK を持つため、先にアカウントを作らないと insert できない。 */
    private fun newOwner(subjectId: String): AccountId =
        accounts
            .save(Account.create(subjectId).getOrThrow { AssertionError(it.toString()) })
            .getOrThrow { AssertionError(it.toString()) }
            .id

    private fun newWorld(ownerId: AccountId, name: String): World =
        World.create(ownerId, name).getOrThrow { AssertionError(it.toString()) }

    private fun save(world: World): Result<World, WorldSaveFailure> = inTransaction {
        worlds.saveIfNameAvailable(world)
    }

    private fun saveWorld(ownerId: AccountId, name: String): World =
        save(newWorld(ownerId, name)).getOrThrow { AssertionError(it.toString()) }

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
        save(renamed).getOrThrow { AssertionError(it.toString()) }

        assert(worlds.findOwnedBy(ownerId, saved.id)?.name == WorldName("新名"))
    }

    @Test
    fun `同名の世界を作ろうとすると例外ではなく NameTaken が返る`() {
        // UNIQUE 制約違反はトランザクションを abort させるため、例外として飛ばしてしまうと 409 に写せない
        // （#739）。名前の裁定を保存と同じ 1 手に閉じ込め、衝突を Err として返すのがこの口の契約。
        val ownerId = newOwner("sub-world-duplicate")
        saveWorld(ownerId, "重複名")

        val result = save(newWorld(ownerId, "重複名"))

        assert(result.getError() == WorldSaveFailure.NameTaken)
        assert(queries.findAllByAccountId(ownerId).size == 1)
    }

    @Test
    fun `同じ名前は他のアカウントとなら重複しない`() {
        val ownerId = newOwner("sub-world-name-scoped")
        val otherId = newOwner("sub-world-name-scoped-other")
        saveWorld(ownerId, "同じ名前")

        val result = save(newWorld(otherId, "同じ名前"))

        assert(result.getError() == null)
    }

    @Test
    fun `名前を変えない保存は自分自身との重複とみなさない`() {
        // 改名の no-op（現在と同じ名前への改名）がこの経路を通る。重複判定が自分自身を除かないと
        // 「自分と衝突している」と誤判定して 409 になる。
        val ownerId = newOwner("sub-world-rename-noop")
        val saved = saveWorld(ownerId, "変わらない名前")

        val result = save(saved.rename("変わらない名前").getOrThrow { AssertionError(it.toString()) })

        assert(result.getError() == null)
    }

    @Test
    fun `古い version の世界を保存すると Conflict が返る`() {
        val ownerId = newOwner("sub-world-stale")
        val saved = saveWorld(ownerId, "楽観ロックの世界")
        val stale = saved.rename("先を越された名前").getOrThrow { AssertionError(it.toString()) }
        save(saved.rename("先に確定した名前").getOrThrow { AssertionError(it.toString()) }).getOrThrow {
            AssertionError(it.toString())
        }

        val result = save(stale)

        assert(result.getError() == WorldSaveFailure.Conflict)
    }

    @Test
    fun `トランザクションの外から保存すると落ちる`() {
        // ロックはトランザクションの終了まで保持されて初めて直列化になる。トランザクションが無いまま
        // 呼ばれると FOR UPDATE が即座に解放されて防御が無症状で消えるため、MANDATORY で顕在化させる。
        val ownerId = newOwner("sub-world-no-transaction")

        val thrown = runCatching {
            worlds.saveIfNameAvailable(newWorld(ownerId, "境界の外"))
        }
            .exceptionOrNull()

        assert(thrown is IllegalTransactionStateException)
    }

    @Test
    fun `同名の世界を saveIfAbsent で二重に保存しても増えず先着が返る`() {
        val ownerId = newOwner("sub-world-if-absent")

        // ID は World.create() が採番するため、2 回目は先着とは別の ID を持つ集約を渡している。
        // 返るのが先着の ID なら「insert せず既存を読み直した」ことの証拠になる。
        val first = worlds.saveIfAbsent(newWorld(ownerId, "はじまりの世界"))
        val second = worlds.saveIfAbsent(newWorld(ownerId, "はじまりの世界"))

        assert(second.id == first.id)
        assert(second.version == first.version)
        assert(queries.findAllByAccountId(ownerId).size == 1)
    }

    @Test
    fun `別名の世界なら saveIfAbsent はそのまま保存する`() {
        val ownerId = newOwner("sub-world-if-absent-distinct")

        worlds.saveIfAbsent(newWorld(ownerId, "1つ目"))
        worlds.saveIfAbsent(newWorld(ownerId, "2つ目"))

        assert(queries.findAllByAccountId(ownerId).size == 2)
    }

    @Test
    fun `saveIfAbsent の初回保存は saveIfNameAvailable と同じ version を採番する`() {
        // saveIfAbsent は upsert のため Spring Data JDBC を通さず INSERT 文を手書きする。
        // 初期 version が saveIfNameAvailable（Spring Data JDBC 採番）とずれると、以後の楽観ロック更新の
        // 前提が経路によって食い違うため、ここで縛る。
        val ownerId = newOwner("sub-world-if-absent-version")
        val bySave = saveWorld(ownerId, "saveIfNameAvailable で作った世界")

        val byIfAbsent = worlds.saveIfAbsent(newWorld(ownerId, "saveIfAbsent で作った世界"))

        assert(byIfAbsent.version == bySave.version)
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
