package com.example.api

import com.example.api.support.PostgresContainerSupport
import com.example.api.support.TestJwtDecoderConfiguration
import org.junit.jupiter.api.Test
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

// datasource は外部供給（H2 全面脱却・#451）のため、コンテキスト起動には
// PostgresContainerSupport の Testcontainers PostgreSQL を注入する。
//
// webEnvironment / @AutoConfigureRestTestClient / @Import は、web 環境を要する @SpringBootTest 5 クラス
// （本クラス / McpDisabledByDefaultTest / HealthEndpointTest / OpenApiTest / SecurityConfigTest）で
// キーを揃えてコンテキストを 1 つ共有するためのもの（#817 / ADR-0077）。本クラス自身は RestTestClient も
// JWT も使わないが、1 つでも構成がずれるとそのクラスだけ別コンテキストへ分岐し、効果（:test -20.5%）が失われる。
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestJwtDecoderConfiguration::class)
class ApiApplicationTests : PostgresContainerSupport() {
    @Test fun contextLoads() {}
}
