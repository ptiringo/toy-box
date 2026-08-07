package com.example.api.support

import com.example.api.domain.shared.generateId
import com.example.api.infrastructure.racing.jockey.JockeyRow
import com.example.api.infrastructure.racing.jockey.JockeySpringDataRepository
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.TestConstructor
import org.springframework.test.context.TestConstructor.AutowireMode

/**
 * DB を触るテストの後始末（[PostgresContainerSupport.truncateAllTables]）そのものの検証（#440 / ADR-0070）。
 *
 * 後始末は基底クラスの `@BeforeEach` に一元化されており、各テストは何も書かなくても空の DB から始まる。 この「効いていること」自体を担保する。
 *
 * 実行順を固定しているのはこのクラスだけの特例で、「前のテストが残した行が次のテストの開始時に 消えている」という順序をまたぐ性質がここでの検証対象そのものであるため。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestConstructor(autowireMode = AutowireMode.ALL)
@TestMethodOrder(OrderAnnotation::class)
class PostgresContainerSupportTest(
    private val jockeyRows: JockeySpringDataRepository,
    private val jdbc: JdbcTemplate,
) : PostgresContainerSupport() {

    @Test
    @Order(1)
    fun `TRUNCATE 対象は Flyway が作った実テーブルを網羅し内部管理テーブルを含まない`() {
        val tables = truncatableTables()

        // 列挙が空振りしても TRUNCATE は無言で成功するため、集合が空でないことを明示的に確かめる
        assert(tables.isNotEmpty())
        // コンテキスト別スキーマ（ADR-0048）の両方から拾えていること
        assert("racing.jockey" in tables)
        assert("studbook.blood_horse" in tables)
        // Flyway の内部管理テーブルは消してはならない
        assert(tables.none { it.endsWith(".flyway_schema_history") })
    }

    @Test
    @Order(2)
    fun `テストが書き込んだ行はそのテストの中では見える`() {
        jockeyRows.save(
            JockeyRow(id = generateId(), worldId = createWorld(), firstName = "武", lastName = "豊")
        )

        assert(jockeyRows.count() == 1L)
    }

    @Test
    @Order(3)
    fun `前のテストが残した行は次のテストの開始時に消えている`() {
        // 直前の `テストが書き込んだ行は…` が入れた 1 行が、基底クラスの @BeforeEach で消えている
        assert(jockeyRows.count() == 0L)
    }

    @Test
    @Order(4)
    fun `createWorld はアカウントと世界を 1 組作る`() {
        val worldId = createWorld("検証用の世界")

        val worlds =
            jdbc.queryForObject(
                "SELECT count(*) FROM iam.world WHERE id = ? AND name = ?",
                Int::class.java,
                worldId,
                "検証用の世界",
            )
        val accounts = jdbc.queryForObject("SELECT count(*) FROM iam.account", Int::class.java)
        assert(worlds == 1)
        assert(accounts == 1)
    }

    @Test
    @Order(5)
    fun `createWorld を 2 回呼ぶと別々のアカウントの別々の世界になる`() {
        val first = createWorld("世界A")
        val second = createWorld("世界B")

        val accounts = jdbc.queryForObject("SELECT count(*) FROM iam.account", Int::class.java)
        assert(first != second)
        // 世界ごとにアカウントを分けるのは、テストが「他人の世界」を組み立てられるようにするため
        assert(accounts == 2)
    }
}
