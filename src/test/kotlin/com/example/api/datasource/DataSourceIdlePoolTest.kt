package com.example.api.datasource

import com.example.api.support.PostgresContainerSupport
import com.zaxxer.hikari.HikariDataSource
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestConstructor
import org.springframework.test.context.TestConstructor.AutowireMode

/**
 * 接続プールが、使っていないあいだ DB に問い合わせ続けないことを検証する（#1041）。
 *
 * 本番 DB（Prisma Postgres の Free プラン）は月の operations に上限があり、超えると接続ごと拒否される。 HikariCP 7
 * の既定値は「接続を常に10本保ち、各接続へ2分おきに死活確認を送り、30分ごとに張り直す」。 uptime check がインスタンスを起こし続けるあいだ、利用が無くても1日に約8,500
 * operations を消費し、2026-11 に上限に達した。
 *
 * そこで、常に保つ接続を 0 本にし、空いた接続は 1 分で閉じ、死活確認を止める。既定値に戻ると静かに上限を食い潰すので、値まで固定する。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestConstructor(autowireMode = AutowireMode.ALL)
class DataSourceIdlePoolTest(private val dataSource: HikariDataSource) :
    PostgresContainerSupport() {
    @Test
    fun `使っていないあいだは接続を保たない`() {
        assert(dataSource.minimumIdle == 0)
    }

    @Test
    fun `空いた接続は 1 分で閉じる`() {
        assert(dataSource.idleTimeout == 60_000L)
    }

    @Test
    fun `接続への死活確認を送らない`() {
        assert(dataSource.keepaliveTime == 0L)
    }
}
