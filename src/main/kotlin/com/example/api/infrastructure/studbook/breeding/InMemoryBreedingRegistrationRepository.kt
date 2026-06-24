package com.example.api.infrastructure.studbook.breeding

import com.example.api.domain.studbook.model.breeding.BreedingRegistration
import com.example.api.domain.studbook.model.breeding.BreedingRegistrationId
import com.example.api.domain.studbook.model.breeding.BreedingRegistrationRepository
import java.util.concurrent.ConcurrentHashMap
import org.springframework.stereotype.Repository

/**
 * In-Memory な BreedingRegistrationRepository 実装。PoC 用途。
 *
 * 永続化層の代わりとして ConcurrentHashMap で保持する。アプリ再起動でデータは消える。
 */
@Repository
class InMemoryBreedingRegistrationRepository : BreedingRegistrationRepository {
    private val store = ConcurrentHashMap<BreedingRegistrationId, BreedingRegistration>()

    override fun findById(id: BreedingRegistrationId): BreedingRegistration? = store[id]

    override fun save(breedingRegistration: BreedingRegistration): BreedingRegistration {
        store[breedingRegistration.id] = breedingRegistration
        return breedingRegistration
    }
}
