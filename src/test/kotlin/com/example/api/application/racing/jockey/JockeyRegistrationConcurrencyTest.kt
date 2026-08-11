package com.example.api.application.racing.jockey

import com.example.api.domain.racing.model.jockey.JockeyId
import com.example.api.domain.shared.AccountId
import com.example.api.domain.shared.Actor
import com.example.api.domain.shared.Command
import com.example.api.domain.shared.Idempotency
import com.example.api.domain.shared.WorldId
import com.example.api.domain.shared.generateId
import com.example.api.support.PostgresContainerSupport
import com.github.michaelbull.result.getOrThrow
import java.time.Clock
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.simple.JdbcClient

/**
 * 同一の冪等キーを持つ再送が並行しても、ジョッキーが 1 頭しか作られないことを実 DB で検証する（#750 / ADR-0072）。
 *
 * `JdbcIdempotencyStore.claim` の 2 手は**別々の競合**を止めており、1 本のテストでは両方を縛れない。
 *
 * - `ON CONFLICT DO NOTHING` … **初回同士**。記録がまだ無いので、UNIQUE 索引が後続を先着の確定まで待たせる
 * - `FOR UPDATE` … **前回失敗のあとの再送同士**。記録は既に commit 済み（`resource_id` は NULL）なので insert
 *   は誰も待たせず、行ロックだけが直列化を担う
 *
 * したがって初回同士のテストは `FOR UPDATE` を外しても緑のままになる（スレッド数を増やしても変わらない）。 2 つ目のテストが `FOR UPDATE`
 * を縛るためにある。それぞれ対応するミューテーションで落ちることを確認すること。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class JockeyRegistrationConcurrencyTest : PostgresContainerSupport() {

    @Autowired private lateinit var registerJockey: JockeyRegistrationUseCase
    @Autowired private lateinit var jdbcClient: JdbcClient

    private lateinit var actor: Actor

    @BeforeEach
    fun createTestWorld() {
        actor = Actor(accountId = AccountId(generateId()), worldId = WorldId(createWorld()))
    }

    private fun registerConcurrently(key: String): List<JockeyId> {
        val barrier = CyclicBarrier(THREADS)
        val executor = Executors.newFixedThreadPool(THREADS)
        return try {
            val tasks =
                List(THREADS) {
                    Callable {
                        barrier.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        registerJockey(
                            actor,
                            Command.now(
                                RegisterJockeyCommand("Yutaka", "Take"),
                                Clock.systemUTC(),
                                Idempotency(key, FINGERPRINT),
                            ),
                        )
                    }
                }
            executor.invokeAll(tasks).map { future ->
                future.get().getOrThrow { AssertionError(it.toString()) }.id
            }
        } finally {
            executor.shutdownNow()
        }
    }

    private fun countJockeys(): Long =
        jdbcClient
            .sql("SELECT count(*) FROM racing.jockey WHERE world_id = :worldId")
            .param("worldId", actor.worldId.value)
            .query(Long::class.java)
            .single()

    /** 「キーは確保したが結果を残さなかった」＝ `resource_id` が NULL のまま commit 済みの記録を作る。 */
    private fun seedClaimedRecord(key: String) {
        jdbcClient
            .sql(
                "INSERT INTO shared.idempotency_record " +
                    "(world_id, idempotency_key, request_fingerprint, created_at) " +
                    "VALUES (:worldId, :key, :fingerprint, now())"
            )
            .param("worldId", actor.worldId.value)
            .param("key", key)
            .param("fingerprint", FINGERPRINT)
            .update()
    }

    @Test
    fun `同じ冪等キーで並行に登録してもジョッキーは 1 頭だけ`() {
        val ids = registerConcurrently("key-concurrent")

        assert(ids.toSet().size == 1)
        assert(countJockeys() == 1L)
    }

    @Test
    fun `前回失敗の記録が残った状態で並行再送してもジョッキーは 1 頭だけ`() {
        // 記録が既にあるので insert は誰も待たせない。直列化は claim の FOR UPDATE だけが担う。
        seedClaimedRecord("key-retry-after-failure")

        val ids = registerConcurrently("key-retry-after-failure")

        assert(ids.toSet().size == 1)
        assert(countJockeys() == 1L)
    }

    private companion object {
        /** 同時に叩くスレッド数。Hikari の既定プール（10）に収まる範囲で窓に入りやすい数にする。 */
        const val THREADS = 4

        /** バリアで待ち合わせる上限。揃わないまま無言でハングさせないための保険。 */
        const val TIMEOUT_SECONDS = 10L

        /** 全スレッドが同じ内容を送る前提なので指紋も 1 つ。seed する記録とも一致させる。 */
        const val FINGERPRINT = "fingerprint-same"
    }
}
