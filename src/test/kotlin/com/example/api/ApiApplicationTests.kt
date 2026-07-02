package com.example.api

import com.example.api.support.PostgresContainerSupport
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

// datasource は外部供給（H2 全面脱却・#451）のため、コンテキスト起動には
// PostgresContainerSupport の Testcontainers PostgreSQL を注入する。
@SpringBootTest
class ApiApplicationTests : PostgresContainerSupport() {
    @Test fun contextLoads() {}
}
