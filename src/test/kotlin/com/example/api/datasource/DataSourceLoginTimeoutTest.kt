package com.example.api.datasource

import com.example.api.support.PostgresContainerSupport
import com.zaxxer.hikari.HikariDataSource
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestConstructor
import org.springframework.test.context.TestConstructor.AutowireMode

/**
 * アプリの datasource が接続確立にも上限を持つことを検証する（#818）。
 *
 * HikariCP の `connectionTimeout` は「プールから接続を借りる待ち時間」であり、接続確立そのものには `DriverManager.setLoginTimeout()`
 * 経由でしか伝わらない。そしてその経路は pgjdbc に効かない （理由と実測は [PgJdbcConnectTimeoutTest]）。そのため `loginTimeout`
 * を接続プロパティとして 明示的に渡す必要があり、渡し忘れると詰まった DB に対して**無限に待つ**。
 *
 * 値が `connectionTimeout` とズレると「借用は 30 秒で諦めるのに接続確立は 5 分待つ」のような ちぐはぐが静かに生まれるため、一致まで含めて固定する。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestConstructor(autowireMode = AutowireMode.ALL)
class DataSourceLoginTimeoutTest(private val dataSource: HikariDataSource) :
    PostgresContainerSupport() {
    @Test
    fun `接続確立のタイムアウトが設定され HikariCP の connectionTimeout と揃っている`() {
        val loginTimeoutSeconds =
            dataSource.dataSourceProperties.getProperty("loginTimeout")?.toIntOrNull() ?: 0

        assert(loginTimeoutSeconds > 0)
        assert(loginTimeoutSeconds.toLong() * MILLIS_PER_SECOND == dataSource.connectionTimeout)
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
    }
}
