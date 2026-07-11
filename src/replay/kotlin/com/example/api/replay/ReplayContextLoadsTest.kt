package com.example.api.replay

import com.example.api.support.PostgresContainerSupport
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

/** replay ソースセットが実 Spring コンテキストを Testcontainers PostgreSQL 上に起動できることの配線スモーク。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ReplayContextLoadsTest : PostgresContainerSupport() {
    @Test fun contextLoads() {}
}
