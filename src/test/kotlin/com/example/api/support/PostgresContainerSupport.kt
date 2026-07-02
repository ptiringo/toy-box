package com.example.api.support

import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * テストが本番ターゲットの PostgreSQL に対して検証するための共有コンテナ（ADR-0027 / #422）。
 *
 * ランタイムの datasource は外部供給（H2 全面脱却・#451。本番 = Prisma Postgres の env 注入、ローカル bootRun = docker-compose
 * 自動配線）で、app.yml に既定値を持たない。そのため Spring コンテキストを起動するテスト （契約テスト・`@SpringBootTest` 統合）は本クラスを継承して
 * Testcontainers の実 PostgreSQL を割り当てる。 コンテナはプロセス内で 1
 * つだけ起動して全テストで共有し（シングルトン）、明示停止はしない（Testcontainers の Ryuk が JVM 終了時に破棄する）。接続先は
 * `@DynamicPropertySource` で各コンテキストへ注入する。注入する値は全テストで同一なので distinct な ApplicationContext
 * を増やさず、コンテキスト キャッシュ方針（ADR-0015 / testing.md）を維持する。
 *
 * 本クラスを継承するテストでは、Flyway が起動時に `db/migration/V*.sql` をこの PostgreSQL コンテナへ適用する （Boot 4.1 + Flyway
 * 12 + flyway-database-postgresql で自動実行されることを併せて担保する）。
 */
// テストが継承して共有コンテナを得るための基底クラス。object 化すると継承できないため、
// 「companion のユーティリティだけなら object にせよ」という detekt の指摘はここでは当たらない。
@Suppress("UtilityClassWithPublicConstructor")
abstract class PostgresContainerSupport {
    companion object {
        @JvmStatic
        private val postgres =
            PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine")).apply { start() }

        @JvmStatic
        @DynamicPropertySource
        fun datasourceProperties(registry: DynamicPropertyRegistry) {
            // driver-class-name は app.yml で明示せず JDBC URL から導出させる方針（#451）。ここでも
            // URL（PostgreSQL）だけ差し替えれば Spring がドライバを PostgreSQL に導出する（本番と同じ経路）。
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }
}
