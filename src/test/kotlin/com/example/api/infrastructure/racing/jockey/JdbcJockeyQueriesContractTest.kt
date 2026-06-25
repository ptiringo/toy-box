package com.example.api.infrastructure.racing.jockey

import com.example.api.domain.racing.model.jockey.JockeyId
import com.example.api.domain.shared.generateId
import com.example.api.support.PostgresContainerSupport
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.context.TestConstructor
import org.springframework.test.context.TestConstructor.AutowireMode

/**
 * 読み取りポート JockeyQueries の Spring Data JDBC とは別経路の実装 [JdbcJockeyQueries] の契約テスト （軽量 CQRS（L2）の Query
 * 側。ADR-0031）。
 *
 * 書き込みの [JdbcJockeyRepositoryContractTest] と同じく本番ターゲットの PostgreSQL（Testcontainers、
 * [PostgresContainerSupport] で共有）に対して検証する。コンテキスト構成も write 側の契約テストと同一 （`@SpringBootTest(NONE)` ＋
 * 同じ動的プロパティ）のため、コンテキストキャッシュを共有する（testing.md）。
 *
 * 検証する契約:
 * 1. `jockey` テーブルへ直接 SELECT し、集約を組まずに [com.example.api.application.racing.jockey.JockeyView]
 *    へ平坦に詰められること（write の `JockeyRow`／集約を経由しない）
 * 2. value class の `JockeyId` ↔ DB `uuid` 列を読み取り経路でも橋渡しできること
 * 3. 該当行が無ければ null を返すこと
 *
 * 行の投入は infrastructure 内部の永続化詳細（[JockeySpringDataRepository]）で行い、読み取りは別経路の [JdbcJockeyQueries]
 * で引く——投入と参照で経路を分けることで「別ポート・別実装」を実機で確かめる。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestConstructor(autowireMode = AutowireMode.ALL)
class JdbcJockeyQueriesContractTest(
    private val jdbcClient: JdbcClient,
    private val rows: JockeySpringDataRepository,
) : PostgresContainerSupport() {

    private val queries = JdbcJockeyQueries(jdbcClient)

    @BeforeEach
    fun cleanUp() {
        rows.deleteAll()
    }

    @Test
    fun `保存済みのジョッキーをIDでJockeyViewとして引ける`() {
        val id = generateId()
        rows.save(JockeyRow(id = id, firstName = "武", lastName = "豊"))

        val view = queries.findById(JockeyId(id))

        assert(view != null)
        assert(view!!.id == id)
        assert(view.firstName == "武")
        assert(view.lastName == "豊")
    }

    @Test
    fun `存在しないIDのfindByIdはnullを返す`() {
        assert(queries.findById(JockeyId(generateId())) == null)
    }
}
