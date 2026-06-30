package com.example.api.e2e

import com.example.api.support.PostgresContainerSupport
import com.intuit.karate.Runner
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort

/**
 * ジョッキー API のブラックボックス E2E（Karate）。
 *
 * アプリを実 port で起動し（@SpringBootTest RANDOM_PORT）、Testcontainers PostgreSQL を
 * [PostgresContainerSupport] 経由で実配線したまま、HTTP 越しに .feature シナリオを流す。 controller → application →
 * infrastructure → 実 DB の結線を本物で検証する。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class JockeyApiE2eTest : PostgresContainerSupport() {

    @LocalServerPort private var port: Int = 0

    @Test
    fun `ジョッキー API の E2E シナリオが全て通ること`() {
        val results =
            Runner.path("classpath:e2e/jockey.feature")
                .systemProperty("karate.server.port", port.toString())
                .parallel(1)
        assert(results.failCount == 0) { results.errorMessages }
    }
}
