package com.example.api.support

import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * 永続化の契約テストが本番ターゲットの PostgreSQL に対して検証するための共有コンテナ（ADR-0027 / #422）。
 *
 * ランタイムの datasource は当面 H2（PostgreSQL 互換モード）だが、永続化の契約テストは本番ターゲット DB と 同じ PostgreSQL
 * で検証したい。そこで本クラスを継承したテストには Testcontainers の実 PostgreSQL を割り当てる。 コンテナはプロセス内で 1
 * つだけ起動して全テストで共有し（シングルトン）、明示停止はしない（Testcontainers の Ryuk が JVM 終了時に破棄する）。接続先は
 * `@DynamicPropertySource` で各コンテキストへ注入し、app.yml の既定 （H2）を上書きする。注入する値は全テストで同一なので distinct な
 * ApplicationContext を増やさず、コンテキスト キャッシュ方針（ADR-0015 / testing.md）を維持する。
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
            // app.yml は driver-class-name を H2 に固定しているため、URL だけ差し替えると
            // ドライバ（H2）と URL（PostgreSQL）が食い違い datasource 初期化に失敗する。ドライバも上書きする。
            registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName)
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }
}
