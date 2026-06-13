package com.example.api.infrastructure.horseracing.jockey

import com.example.api.domain.horseracing.model.jockey.Jockey
import com.example.api.domain.horseracing.model.jockey.JockeyId
import com.example.api.domain.horseracing.model.jockey.JockeyRepository
import java.util.concurrent.ConcurrentHashMap
import org.springframework.stereotype.Repository

/**
 * In-Memory な JockeyRepository 実装。PoC 用途。
 *
 * 永続化層の代わりとして ConcurrentHashMap で保持する。アプリ再起動でデータは消える。
 */
@Repository
class InMemoryJockeyRepository : JockeyRepository {
    private val store = ConcurrentHashMap<JockeyId, Jockey>()

    override fun findByFullName(firstName: String, lastName: String): Jockey? =
        store.values.firstOrNull { it.firstName == firstName && it.lastName == lastName }

    override fun save(jockey: Jockey): Jockey {
        store[jockey.id] = jockey
        return jockey
    }
}
