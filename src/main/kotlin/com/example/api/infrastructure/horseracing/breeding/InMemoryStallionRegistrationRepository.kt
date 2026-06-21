package com.example.api.infrastructure.horseracing.breeding

import com.example.api.domain.horseracing.model.breeding.StallionRegistration
import com.example.api.domain.horseracing.model.breeding.StallionRegistrationId
import com.example.api.domain.horseracing.model.breeding.StallionRegistrationRepository
import java.util.concurrent.ConcurrentHashMap
import org.springframework.stereotype.Repository

/**
 * In-Memory な StallionRegistrationRepository 実装。PoC 用途。
 *
 * 永続化層の代わりとして ConcurrentHashMap で保持する。アプリ再起動でデータは消える。
 */
@Repository
class InMemoryStallionRegistrationRepository : StallionRegistrationRepository {
    private val store = ConcurrentHashMap<StallionRegistrationId, StallionRegistration>()

    override fun findById(id: StallionRegistrationId): StallionRegistration? = store[id]

    override fun save(stallionRegistration: StallionRegistration): StallionRegistration {
        store[stallionRegistration.id] = stallionRegistration
        return stallionRegistration
    }
}
