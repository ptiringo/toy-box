package com.example.api.application.iam.world

import com.example.api.domain.iam.model.account.Account
import com.example.api.domain.iam.model.account.AccountRepository
import com.example.api.domain.shared.AccountId
import com.example.api.domain.shared.Command
import com.example.api.domain.shared.WorldId
import com.example.api.support.PostgresContainerSupport
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.getOrThrow
import java.time.Clock
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.simple.JdbcClient

/**
 * 世界の作成・改名が名前を巡って並行競合しても 500 にならないことを実 DB で検証する（#739）。
 *
 * 「世界を作る」ボタンの二度押し・送信後のリロードで、同じ名前の作成や改名は**現実に同時実行される**。 事前照会と保存が別々の呼び出しに分かれていると、その 2
 * 手のあいだに別のリクエストが同名を書き込む TOCTOU が残り、負けた側は未捕捉の `DuplicateKeyException`（＝500）になる。名前の裁定と保存を
 * [com.example.api.domain.iam.model.world.WorldRepository.saveIfNameAvailable] の 1 手に閉じ込め、その内側で
 * アカウント単位に直列化することで、負けた側にも 409 相当の `Conflict` が返る。
 *
 * レースは本質的に確率的なので、[CyclicBarrier] で全スレッドの発火点を揃えて窓に入りやすくしている。 タイミング次第で競合せずに終わる回もあるが、その場合も assert
 * は成立する（レースを踏んだ回だけが追加で 意味を持つ「片側検出」のテスト。#713 の `ProvisionMeConcurrencyTest` と同型）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class WorldNameConcurrencyTest : PostgresContainerSupport() {

    @Autowired private lateinit var createWorld: CreateWorldUseCase
    @Autowired private lateinit var renameWorld: RenameWorldUseCase
    @Autowired private lateinit var accounts: AccountRepository
    @Autowired private lateinit var jdbcClient: JdbcClient

    private val clock = Clock.systemUTC()

    /** 世界は account への FK を持つため、先にアカウントを作らないと insert できない。 */
    private fun newOwner(subjectId: String): AccountId =
        accounts
            .save(Account.create(subjectId).getOrThrow { AssertionError(it.toString()) })
            .getOrThrow { AssertionError(it.toString()) }
            .id

    /**
     * 全スレッドの発火点を揃えて [task] を同時に走らせ、各スレッドの結果を返す。
     *
     * `Future.get()` は本体が投げた例外を `ExecutionException` で包んで再送出する。名前の競合が例外として 漏れていれば（＝本番なら
     * 500）ここでテストが落ちる。
     */
    private fun <T> concurrently(task: (Int) -> T): List<T> {
        val barrier = CyclicBarrier(THREADS)
        val executor = Executors.newFixedThreadPool(THREADS)
        return try {
            val tasks =
                List(THREADS) { index ->
                    Callable {
                        barrier.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        task(index)
                    }
                }
            executor.invokeAll(tasks).map { future -> future.get() }
        } finally {
            executor.shutdownNow()
        }
    }

    private fun createWorldNamed(ownerId: AccountId, name: String): WorldId =
        WorldId(
            createWorld(ownerId, Command.now(CreateWorldCommand(name), clock))
                .getOrThrow { AssertionError(it.toString()) }
                .id
        )

    private fun countWorlds(ownerId: AccountId): Long =
        countWorlds("SELECT count(*) FROM iam.world WHERE account_id = :accountId") {
            it.param("accountId", ownerId.value)
        }

    private fun countWorldsNamed(ownerId: AccountId, name: String): Long =
        countWorlds(
            "SELECT count(*) FROM iam.world WHERE account_id = :accountId AND name = :name"
        ) {
            it.param("accountId", ownerId.value).param("name", name)
        }

    private fun countWorlds(
        sql: String,
        bind: (JdbcClient.StatementSpec) -> JdbcClient.StatementSpec,
    ): Long = bind(jdbcClient.sql(sql)).query(Long::class.java).single()

    @Test
    fun `同名の世界を並行作成しても 1 つしか作られず負けた側は競合になる`() {
        val ownerId = newOwner("sub-concurrent-create-world")

        val results =
            concurrently<Result<WorldView, CreateWorldError>> {
                createWorld(ownerId, Command.now(CreateWorldCommand("同じ名前の牧場"), clock))
            }

        val errors = results.mapNotNull { it.getError() }
        assert(errors.size == THREADS - 1)
        assert(errors.all { it is CreateWorldError.Conflict })
        assert(countWorlds(ownerId) == 1L)
    }

    @Test
    fun `別々の世界を同じ名前へ並行改名しても 1 つしか成功せず負けた側は競合になる`() {
        val ownerId = newOwner("sub-concurrent-rename-world")
        val worldIds = List(THREADS) { index -> createWorldNamed(ownerId, "牧場$index") }

        val results =
            concurrently<Result<WorldView, WorldMutationError>> { index ->
                renameWorld(
                    ownerId,
                    Command.now(RenameWorldCommand(worldIds[index], "統一する名前"), clock),
                )
            }

        val errors = results.mapNotNull { it.getError() }
        assert(errors.size == THREADS - 1)
        assert(errors.all { it is WorldMutationError.Conflict })
        assert(countWorldsNamed(ownerId, "統一する名前") == 1L)
        assert(countWorlds(ownerId) == THREADS.toLong())
    }

    @Test
    fun `同じ世界を並行改名しても例外は漏れず負けた側は競合になる`() {
        // 名前は全て別なので重複ではなく楽観ロックの競合を踏む経路。ここが例外で抜けると、ユースケースの
        // トランザクション境界が UnexpectedRollbackException になって 409 のつもりが 500 になる（#739）。
        val ownerId = newOwner("sub-concurrent-rename-same-world")
        val worldId = createWorldNamed(ownerId, "元の名前")

        val results =
            concurrently<Result<WorldView, WorldMutationError>> { index ->
                renameWorld(ownerId, Command.now(RenameWorldCommand(worldId, "新しい名前$index"), clock))
            }

        val errors = results.mapNotNull { it.getError() }
        assert(errors.all { it is WorldMutationError.Conflict })
        assert(errors.size < THREADS)
        assert(countWorlds(ownerId) == 1L)
    }

    private companion object {
        /** 同時に叩くスレッド数。Hikari の既定プール（10）に収まる範囲で窓に入りやすい数にする。 */
        const val THREADS = 4

        /** バリアで待ち合わせる上限。揃わないまま無言でハングさせないための保険。 */
        const val TIMEOUT_SECONDS = 10L
    }
}
