package com.example.api.infrastructure.shared.idempotency

import com.example.api.application.shared.idempotency.IdempotencyStore
import com.example.api.domain.shared.WorldId
import com.example.api.domain.shared.generateId
import com.example.api.support.PostgresContainerSupport
import java.util.UUID
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/** [JdbcIdempotencyStore] がポート [IdempotencyStore] の契約を満たすことを実 DB で検証する。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class JdbcIdempotencyStoreContractTest : PostgresContainerSupport() {

    @Autowired private lateinit var store: IdempotencyStore

    // 基底クラスの TRUNCATE がフィールド初期化の後に走るため、世界は @BeforeEach で作る（testing.md）。
    private lateinit var rawWorldId: UUID
    private val worldId
        get() = WorldId(rawWorldId)

    @BeforeEach
    fun createTestWorld() {
        rawWorldId = createWorld()
    }

    @Test
    fun `初回の claim は結果未記録の記録を返す`() {
        val record = store.claim(worldId, "key-first", "fingerprint-first")

        assert(record.requestFingerprint == "fingerprint-first")
        assert(record.resourceId == null)
    }

    @Test
    fun `同じキーの claim は先着の指紋を返す`() {
        store.claim(worldId, "key-reused", "fingerprint-original")

        val record = store.claim(worldId, "key-reused", "fingerprint-different")

        assert(record.requestFingerprint == "fingerprint-original")
    }

    @Test
    fun `記録した結果 ID が次の claim で返る`() {
        val resourceId = generateId()
        store.claim(worldId, "key-recorded", "fingerprint-recorded")

        store.recordResource(worldId, "key-recorded", resourceId)

        val recorded = store.claim(worldId, "key-recorded", "fingerprint-recorded")
        assert(recorded.resourceId == resourceId)
    }

    @Test
    fun `世界が違えば同じキーを独立に使える`() {
        val otherWorldId = WorldId(createWorld("別の世界"))
        val resourceId = generateId()
        store.claim(worldId, "key-shared", "fingerprint-a")
        store.recordResource(worldId, "key-shared", resourceId)

        val record = store.claim(otherWorldId, "key-shared", "fingerprint-b")

        assert(record.requestFingerprint == "fingerprint-b")
        assert(record.resourceId == null)
    }
}
