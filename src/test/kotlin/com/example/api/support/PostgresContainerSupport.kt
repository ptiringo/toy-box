package com.example.api.support

import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
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
 *
 * **DB の後始末も本クラスが担う**（#440 / ADR-0070）。コンテナはプロセス内で共有されるため、テストが書いた行は 明示的に消さない限り後続のテストへ漏れる。
 * [truncateAllTables] を `@BeforeEach` に置くことで、継承した時点で 必ず後始末が効き、テスト側が書き忘れうる余地を無くしている。`@Transactional`
 * によるロールバック分離は 採らない（トランザクション意味論を検証するテストが実コミットを要求するため適用範囲を 100% にできず、 隔離方式が二重化する。ADR-0070）。
 *
 * **並列実行からの隔離も本クラスが担う**（#690 / ADR-0079）。テストはクラス間並列で走るが、全テーブル TRUNCATE
 * 方式は並行実行と両立しない（他スレッドのデータまで消す）。クラス注釈の `@Execution(SAME_THREAD)` が `@Inherited` で継承先すべてに効き、DB
 * を触るテストだけを 1 スレッドへ閉じ込める。
 */
// テストが継承して共有コンテナを得るための基底クラス。object 化すると継承できないため、
// 「companion のユーティリティだけなら object にせよ」という detekt の指摘はここでは当たらない。
@Suppress("UtilityClassWithPublicConstructor")
// DB を触るテストを 1 スレッドへ閉じ込める（#690 / ADR-0079）。クラス間並列の下では、
// [truncateAllTables] が共有コンテナの全テーブルを消す（ADR-0070）ため、並行して走る別の DB テストの
// データまで消してしまう。@Execution は @Inherited なので、本クラスを継承するテストすべてに効く。
// **この 1 行が DB テストの隔離を支えているので外さないこと。**
@Execution(ExecutionMode.SAME_THREAD)
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

        /**
         * Flyway が作った実テーブルを `スキーマ名.テーブル名` の完全修飾名で列挙する。
         *
         * スキーマはコンテキスト別に分かれ（ADR-0048）今後も増えるため、スキーマ名もテーブル名もハードコード しない。除外するのはシステムスキーマと Flyway
         * の内部管理テーブル（`flyway_schema_history`。V13 の方針で 既定スキーマに残る）だけ。
         */
        fun truncatableTables(): List<String> = connect().use { selectTableNames(it) }

        /** テストから DB へ直接つなぐ（後始末とフィクスチャ投入の両方が使う）。 */
        internal fun connect(): Connection =
            DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password)

        private fun selectTableNames(connection: Connection): List<String> =
            connection.createStatement().use { statement ->
                statement.executeQuery(TABLE_NAME_QUERY).use { resultSet ->
                    buildList { while (resultSet.next()) add(resultSet.getString(1)) }
                }
            }

        private val TABLE_NAME_QUERY =
            """
            SELECT format('%I.%I', schemaname, tablename)
            FROM pg_tables
            WHERE schemaname NOT IN ('pg_catalog', 'information_schema')
                AND tablename <> 'flyway_schema_history'
            ORDER BY 1
            """
                .trimIndent()
    }

    /**
     * 各テストの前に全テーブルを空にする。
     *
     * Spring の `JdbcClient` ではなくコンテナへ直接つなぐのは、テスト側の接続やトランザクション状態、 `@SpringBootTest`
     * の構成に後始末を依存させないため。全テーブルを 1 文でまとめて TRUNCATE するので FK の依存順（ADR-0053）を考える必要が無い。
     */
    @BeforeEach
    fun truncateAllTables() {
        connect().use { connection ->
            val tables = selectTableNames(connection)
            // 列挙が空振りしても TRUNCATE は無言で成功し「後始末しているつもり」になるため、ここで落とす
            check(tables.isNotEmpty()) { "TRUNCATE 対象のテーブルが 1 つも見つからない（Flyway 未適用か列挙条件の誤り）" }
            connection.createStatement().use { statement ->
                statement.execute("TRUNCATE TABLE ${tables.joinToString()} CASCADE")
            }
        }
    }

    /**
     * テスト用のアカウントと世界を 1 組作り、世界のIDを返す（#704 / ADR-0067）。
     *
     * 世界スコープ化以降、ドメインの行は必ずいずれかの世界に属する（`world_id` の FK）。その前提を各テストが
     * 手で組むと重複するため基底クラスに置く。[truncateAllTables] が毎テスト前に全テーブルを空にするので、 テストごとに呼び直すこと。
     *
     * 呼ぶたびに**アカウントも新しく作る**。「他人の世界」を組み立てられることが、世界をまたぐ参照の拒否を 検証するテストの前提になるため。
     *
     * 認証の経路（`ActorArgumentResolver`）を通さず直接 insert するのは、ここで用意したいのが「誰として リクエストするか」ではなく「行が属する世界」だけだから。
     */
    protected fun createWorld(name: String = "テスト世界"): UUID {
        val accountId = UUID.randomUUID()
        val worldId = UUID.randomUUID()
        connect().use { connection ->
            connection
                .prepareStatement(
                    "INSERT INTO iam.account (id, subject_id, version) VALUES (?, ?, 0)"
                )
                .use { statement ->
                    statement.setObject(1, accountId)
                    statement.setString(2, "test-subject-$accountId")
                    statement.executeUpdate()
                }
            connection
                .prepareStatement(
                    "INSERT INTO iam.world (id, account_id, name, version) VALUES (?, ?, ?, 0)"
                )
                .use { statement ->
                    statement.setObject(1, worldId)
                    statement.setObject(2, accountId)
                    statement.setString(3, name)
                    statement.executeUpdate()
                }
        }
        return worldId
    }
}
