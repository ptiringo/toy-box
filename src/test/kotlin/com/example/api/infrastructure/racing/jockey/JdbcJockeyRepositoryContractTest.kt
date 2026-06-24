package com.example.api.infrastructure.racing.jockey

import com.example.api.domain.racing.model.jockey.Jockey
import com.example.api.domain.shared.generateId
import com.example.api.support.PostgresContainerSupport
import com.github.michaelbull.result.unwrap
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestConstructor
import org.springframework.test.context.TestConstructor.AutowireMode

/**
 * [#422] ドメインポート JockeyRepository の Spring Data JDBC 実装 [JdbcJockeyRepository] の契約テスト （ADR-0027）。
 *
 * spike（H2・インメモリ）を卒業し、本番ターゲットと同じ PostgreSQL（Testcontainers、[PostgresContainerSupport]
 * で共有）に対して検証する。スキーマはマイグレーション（`db/migration/V*.sql`）を Flyway が起動時に適用して用意する （Boot 4.1 + Flyway 12 +
 * flyway-database-postgresql で自動実行されることもこのテストの成立で担保される。旧 spike の積み残しを解消）。
 *
 * 検証する契約:
 * 1. value class の `JockeyId` ↔ DB `uuid` 列を、永続化モデル分離＋手書きマッパーで橋渡しできること
 * 2. 外部採番（UUIDv7）で `@Id` が常に非 null でも、`@Version` が null のとき insert と判定されること（落とし穴②）
 * 3. 既存行の update で `@Version` がインクリメントされること（楽観ロック兼用。落とし穴③）
 * 4. イミュータブル集約 [Jockey] を ID を保ったまま再構成（reconstitute）して往復できること
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestConstructor(autowireMode = AutowireMode.ALL)
class JdbcJockeyRepositoryContractTest(private val rows: JockeySpringDataRepository) :
    PostgresContainerSupport() {

    private val repository = JdbcJockeyRepository(rows)

    @BeforeEach
    fun cleanUp() {
        rows.deleteAll()
    }

    @Test
    fun `外部採番のIDを持つ新規行はversionがnullなのでinsertされる`() {
        val id = generateId()
        val saved = rows.save(JockeyRow(id = id, firstName = "武", lastName = "豊"))

        // 外部採番した ID がそのまま採用され、新規 insert で version が採番される（null ではなくなる）
        assert(saved.id == id)
        assert(saved.version != null)
        assert(rows.count() == 1L)
        assert(rows.findById(id).isPresent)
    }

    @Test
    fun `既存行をupdateするとversionがインクリメントされる`() {
        val id = generateId()
        val inserted = rows.save(JockeyRow(id = id, firstName = "幸", lastName = "福永"))

        val updated = rows.save(inserted.copy(firstName = "祐一"))

        // 同じ ID のまま update され、version が進む（insert 後 → update 後）
        assert(updated.id == id)
        assert(updated.version!! > inserted.version!!)
        assert(rows.count() == 1L)
        assert(rows.findById(id).orElseThrow().firstName == "祐一")
    }

    @Test
    fun `ドメイン集約をsaveしfindByFullNameでID不変のまま再構成できる`() {
        val jockey = Jockey.create("克典", "横山").unwrap()

        val saved = repository.save(jockey)
        val found = repository.findByFullName("克典", "横山")

        // value class ID が DB 往復しても保たれ、ID ベースの等価性で同一集約とみなせる
        assert(saved.id == jockey.id)
        assert(found != null)
        assert(found == jockey)
        assert(found!!.firstName == "克典")
        assert(found.lastName == "横山")
    }

    @Test
    fun `存在しない名前のfindByFullNameはnullを返す`() {
        assert(repository.findByFullName("該当", "無し") == null)
    }
}
