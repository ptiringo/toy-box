package com.example.api.infrastructure.iam

import com.example.api.support.PostgresContainerSupport
import java.util.UUID
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.JdbcTemplate

/**
 * `iam` スキーマの DB レベル制約に対する契約テスト。
 *
 * 世界名の一意性（アカウント内）と、アカウント削除時の世界の連鎖削除は DB 制約だけが担保しており、 Kotlin 側のコードには対応物が無い。したがってここで直接 SQL を流して確かめる。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class IamSchemaContractTest : PostgresContainerSupport() {

    @Autowired private lateinit var jdbc: JdbcTemplate

    private fun insertAccount(subjectId: String): UUID {
        val id = UUID.randomUUID()
        jdbc.update(
            "INSERT INTO iam.account (id, subject_id, version) VALUES (?, ?, 0)",
            id,
            subjectId,
        )
        return id
    }

    private fun insertWorld(accountId: UUID, name: String): UUID {
        val id = UUID.randomUUID()
        jdbc.update(
            "INSERT INTO iam.world (id, account_id, name, version) VALUES (?, ?, ?, 0)",
            id,
            accountId,
            name,
        )
        return id
    }

    @Test
    fun `同じアカウントに同名の世界は作れない`() {
        val accountId = insertAccount("sub-duplicate-world-name")
        insertWorld(accountId, "はじまりの牧場")

        var rejected = false
        try {
            insertWorld(accountId, "はじまりの牧場")
        } catch (_: DuplicateKeyException) {
            rejected = true
        }

        assert(rejected)
    }

    @Test
    fun `別のアカウントであれば同名の世界を作れる`() {
        val first = insertAccount("sub-world-name-a")
        val second = insertAccount("sub-world-name-b")

        insertWorld(first, "はじまりの牧場")
        insertWorld(second, "はじまりの牧場")

        val count =
            jdbc.queryForObject(
                "SELECT count(*) FROM iam.world WHERE name = ?",
                Int::class.java,
                "はじまりの牧場",
            )
        assert(count == 2)
    }

    @Test
    fun `アカウントを削除すると配下の世界も消える`() {
        val accountId = insertAccount("sub-cascade")
        insertWorld(accountId, "消える世界")

        jdbc.update("DELETE FROM iam.account WHERE id = ?", accountId)

        val remaining =
            jdbc.queryForObject(
                "SELECT count(*) FROM iam.world WHERE account_id = ?",
                Int::class.java,
                accountId,
            )
        assert(remaining == 0)
    }

    @Test
    fun `同じ subject_id のアカウントは二重に作れない`() {
        insertAccount("sub-unique")

        var rejected = false
        try {
            insertAccount("sub-unique")
        } catch (_: DuplicateKeyException) {
            rejected = true
        }

        assert(rejected)
    }
}
