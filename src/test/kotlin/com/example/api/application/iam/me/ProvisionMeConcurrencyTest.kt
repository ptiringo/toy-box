package com.example.api.application.iam.me

import com.example.api.domain.iam.model.account.Account
import com.example.api.domain.iam.model.account.AccountRepository
import com.example.api.domain.shared.AccountId
import com.example.api.domain.shared.Command
import com.example.api.support.PostgresContainerSupport
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
 * `POST /api/me:provision` の並行実行が壊れないことを実 DB で検証する（#713）。
 *
 * この経路はサインイン直後にフロントエンドが必ず 1 回叩く設計で、StrictMode の二重発火・複数タブ・ ログイン直後のリロードで**現実に同時実行される**。事前照会と insert
 * のあいだの TOCTOU を DB の UNIQUE 制約が 裁定するため、競合しても例外は飛ばず、アカウントも世界も増えない。
 *
 * レースは本質的に確率的なので、[CyclicBarrier] で全スレッドの発火点を揃えて窓に入りやすくしている。 タイミング次第で競合せずに終わる回もあるが、その場合も assert
 * は成立する（レースを踏んだ回だけが 追加で意味を持つ「片側検出」のテスト）。実装を `saveIfAbsent` から `save` へ戻すと、このテストは未捕捉の
 * `DuplicateKeyException` で落ちることを確認している。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProvisionMeConcurrencyTest : PostgresContainerSupport() {

    @Autowired private lateinit var provisionMe: ProvisionMeUseCase
    @Autowired private lateinit var accounts: AccountRepository
    @Autowired private lateinit var jdbcClient: JdbcClient

    /** 全スレッドの発火点を揃えて同時に `:provision` を叩き、各スレッドが得た [AccountId] を返す。 */
    private fun provisionConcurrently(subjectId: String): List<AccountId> {
        val barrier = CyclicBarrier(THREADS)
        val executor = Executors.newFixedThreadPool(THREADS)
        return try {
            val tasks =
                List(THREADS) {
                    Callable {
                        barrier.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        provisionMe(Command.now(ProvisionMeCommand(subjectId), Clock.systemUTC()))
                    }
                }
            // Future.get() は本体が投げた例外を ExecutionException で包んで再送出する。
            // UNIQUE 制約違反が漏れていれば（＝本番なら 500）ここでテストが落ちる。
            executor.invokeAll(tasks).map { future ->
                future.get().getOrThrow { AssertionError(it.toString()) }
            }
        } finally {
            executor.shutdownNow()
        }
    }

    private fun countAccounts(subjectId: String): Long =
        jdbcClient
            .sql("SELECT count(*) FROM iam.account WHERE subject_id = :subjectId")
            .param("subjectId", subjectId)
            .query(Long::class.java)
            .single()

    private fun countWorlds(accountId: AccountId): Long =
        jdbcClient
            .sql("SELECT count(*) FROM iam.world WHERE account_id = :accountId")
            .param("accountId", accountId.value)
            .query(Long::class.java)
            .single()

    @Test
    fun `未登録の subject を並行セットアップしてもアカウントと世界は 1 つずつ`() {
        val subjectId = "sub-concurrent-provision"

        val accountIds = provisionConcurrently(subjectId)

        // 全スレッドが同じアカウントを見ている（先着の裁定に全員が従っている）。
        assert(accountIds.toSet().size == 1)
        assert(countAccounts(subjectId) == 1L)
        assert(countWorlds(accountIds.first()) == 1L)
    }

    @Test
    fun `アカウントだけある状態で並行セットアップしても世界は 1 つだけ`() {
        // アカウントを先に作っておくと全スレッドが世界の insert まで到達し、
        // UNIQUE (account_id, name) 側のレースを踏む（アカウント側で待たされないため）。
        val subjectId = "sub-concurrent-first-world"
        val account =
            accounts
                .save(Account.create(subjectId).getOrThrow { AssertionError(it.toString()) })
                .getOrThrow { AssertionError(it.toString()) }

        val accountIds = provisionConcurrently(subjectId)

        assert(accountIds.toSet() == setOf(account.id))
        assert(countWorlds(account.id) == 1L)
    }

    private companion object {
        /** 同時に叩くスレッド数。Hikari の既定プール（10）に収まる範囲で窓に入りやすい数にする。 */
        const val THREADS = 4

        /** バリアで待ち合わせる上限。揃わないまま無言でハングさせないための保険。 */
        const val TIMEOUT_SECONDS = 10L
    }
}
