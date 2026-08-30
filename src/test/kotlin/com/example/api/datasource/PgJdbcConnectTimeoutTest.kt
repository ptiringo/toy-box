package com.example.api.datasource

import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.sql.DriverManager
import java.util.Properties
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import org.junit.jupiter.api.Test

/**
 * 「TCP は accept するが 1 バイトも返さない DB」に対し、pgjdbc の接続確立がいつ諦めるかを固定する（#818）。
 *
 * pre-push が 7 分近く無反応になった事象（`HikariPool-1 - Starting...` から 402.9 秒の空白）の原因は、 **接続確立フェーズに実効的なタイムアウトが
 * 1 つも掛かっていない**ことだった。TCP connect 自体は `connectTimeout`（既定 10 秒）で切れるが、その後の startup / 認証の応答待ちは
 * `socketTimeout` が 0（既定）のとき無制限に待つ。
 *
 * HikariCP は `connectionTimeout` を接続確立にも効かせようとして `DriverManager.setLoginTimeout()` を
 * 呼ぶ（`PoolBase#setLoginTimeout` → `DriverDataSource#setLoginTimeout`）が、pgjdbc の `Driver#timeout()`
 * は `PGProperty.LOGIN_TIMEOUT.getOrDefault(props)` を先に読み、`loginTimeout` の **既定値が文字列 "0"** であるため
 * `timeout <= 0` の分岐に入る。`DriverManager.getLoginTimeout()` への フォールバックには到達せず、HikariCP の意図はドライバに届かない。
 *
 * よって接続確立を有限時間で諦めさせるには `loginTimeout` を接続プロパティとして明示的に渡すしかない （`application.yml` の
 * `spring.datasource.hikari.data-source-properties.loginTimeout`）。
 * 本テストはその前提（＝上流の挙動）を固定する。上流が既定値を改めてフォールバックが効くようになれば 1 つ目が落ちるので、そのとき設定の要否を再判断する。
 */
class PgJdbcConnectTimeoutTest {
    /** 接続試行が待ち上限内に戻ったか。 */
    private enum class ConnectOutcome {
        /** 有限時間で（例外として）諦めた。 */
        SETTLED,
        /** 待ち上限を過ぎても戻ってこない。 */
        STILL_BLOCKED,
    }

    @Test
    fun `HikariCP が設定する DriverManager の loginTimeout では接続確立が止まらない`() {
        withUnresponsiveServer { port ->
            val original = DriverManager.getLoginTimeout()
            // HikariCP が connectionTimeout から設定するもの。テストのため 1 秒へ縮める。
            // pgjdbc はこの値を読まないので、並行して走る他テストの接続には影響しない。
            DriverManager.setLoginTimeout(1)
            try {
                val outcome = attemptConnect(port, loginTimeoutSeconds = null, waitMillis = 2_000)

                assert(outcome == ConnectOutcome.STILL_BLOCKED)
            } finally {
                DriverManager.setLoginTimeout(original)
            }
        }
    }

    @Test
    fun `loginTimeout を接続プロパティで渡せば応答しない DB でも接続確立が有限時間で失敗する`() {
        withUnresponsiveServer { port ->
            val outcome = attemptConnect(port, loginTimeoutSeconds = 1, waitMillis = 5_000)

            assert(outcome == ConnectOutcome.SETTLED)
        }
    }

    /**
     * 接続を [waitMillis] だけ待ち、戻ってきたかどうかを返す。
     *
     * 接続は成功しない前提なので、戻り値が接続かエラーかは問わない（どちらも「諦めた」）。呼び出しスレッドを ブロックさせないため別スレッドで試行し、待ち上限を過ぎたら放置する（daemon
     * なので JVM 終了を妨げない）。
     */
    private fun attemptConnect(
        port: Int,
        loginTimeoutSeconds: Int?,
        waitMillis: Long,
    ): ConnectOutcome {
        val properties =
            Properties().apply {
                setProperty("user", "unused")
                setProperty("password", "unused")
                loginTimeoutSeconds?.let { setProperty("loginTimeout", it.toString()) }
            }
        val executor = Executors.newSingleThreadExecutor { Thread(it).apply { isDaemon = true } }
        try {
            val attempt = executor.submit {
                runCatching {
                    DriverManager.getConnection(
                            "jdbc:postgresql://127.0.0.1:$port/test",
                            properties,
                        )
                        .close()
                }
            }
            return try {
                attempt.get(waitMillis, TimeUnit.MILLISECONDS)
                ConnectOutcome.SETTLED
            } catch (_: TimeoutException) {
                ConnectOutcome.STILL_BLOCKED
            }
        } finally {
            executor.shutdownNow()
        }
    }

    /**
     * 詰まった DB を模擬する。接続は受け付けるが読みも書きもしないので、クライアントは startup メッセージへの 応答を待ち続ける（コンテナは起動しているのに Postgres
     * が応答を返せない状態と同じ形）。
     */
    private fun <T> withUnresponsiveServer(block: (Int) -> T): T {
        val accepted = mutableListOf<Socket>()
        ServerSocket(0, BACKLOG, InetAddress.getLoopbackAddress()).use { server ->
            val accepter = Thread {
                // 閉じられるまで受け付け続ける。close() 後の accept は例外になるのでそこで終わる。
                runCatching {
                    while (true) {
                        accepted += server.accept()
                    }
                }
            }
            accepter.isDaemon = true
            accepter.start()
            try {
                return block(server.localPort)
            } finally {
                accepted.forEach { socket -> runCatching { socket.close() } }
            }
        }
    }

    private companion object {
        const val BACKLOG = 50
    }
}
