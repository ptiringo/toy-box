package com.example.api.infrastructure.shared.idempotency

import com.example.api.application.shared.idempotency.IdempotencyRecord
import com.example.api.application.shared.idempotency.IdempotencyStore
import com.example.api.domain.shared.WorldId
import java.util.UUID
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

/**
 * ポート [IdempotencyStore] の唯一の実装。
 *
 * 集約ではなく単一の記録を扱うだけなので Spring Data JDBC のリポジトリは介さず、[JdbcClient] で SQL を直接書く。
 */
@Repository
class JdbcIdempotencyStore(private val jdbcClient: JdbcClient) : IdempotencyStore {

    /**
     * `ON CONFLICT DO NOTHING` で行を確保してから `FOR UPDATE` で読む。
     *
     * 2 手に分かれているのは、直列化したい競合が 2 種類あるため。insert は**初回同士**の並行を UNIQUE 索引で 直列化し、`FOR UPDATE`
     * は**再送同士**の並行を行ロックで直列化する（前回が失敗して `resource_id` が NULL のまま残っているとき、再送 2 本が同時に来ても片方しか実処理へ進まない）。
     */
    override fun claim(
        worldId: WorldId,
        key: String,
        requestFingerprint: String,
    ): IdempotencyRecord {
        jdbcClient
            .sql(
                "INSERT INTO shared.idempotency_record " +
                    "(world_id, idempotency_key, request_fingerprint, created_at) " +
                    "VALUES (:worldId, :key, :fingerprint, now()) " +
                    "ON CONFLICT (world_id, idempotency_key) DO NOTHING"
            )
            .param("worldId", worldId.value)
            .param("key", key)
            .param("fingerprint", requestFingerprint)
            .update()
        return checkNotNull(lockAndRead(worldId, key)) { "claim 直後に記録を引けない: $key" }
    }

    override fun recordResource(worldId: WorldId, key: String, resourceId: UUID) {
        jdbcClient
            .sql(
                "UPDATE shared.idempotency_record SET resource_id = :resourceId " +
                    "WHERE world_id = :worldId AND idempotency_key = :key"
            )
            .param("resourceId", resourceId)
            .param("worldId", worldId.value)
            .param("key", key)
            .update()
    }

    private fun lockAndRead(worldId: WorldId, key: String): IdempotencyRecord? =
        jdbcClient
            .sql(
                "SELECT request_fingerprint, resource_id FROM shared.idempotency_record " +
                    "WHERE world_id = :worldId AND idempotency_key = :key FOR UPDATE"
            )
            .param("worldId", worldId.value)
            .param("key", key)
            .query { rs, _ ->
                IdempotencyRecord(
                    requestFingerprint = rs.getString("request_fingerprint"),
                    resourceId = rs.getObject("resource_id", UUID::class.java),
                )
            }
            .list()
            .firstOrNull()
}
