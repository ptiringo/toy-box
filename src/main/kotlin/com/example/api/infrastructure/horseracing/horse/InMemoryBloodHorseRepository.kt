package com.example.api.infrastructure.horseracing.horse

import com.example.api.domain.horseracing.model.horse.bloodhorse.BloodHorse
import com.example.api.domain.horseracing.model.horse.bloodhorse.BloodHorseId
import com.example.api.domain.horseracing.model.horse.bloodhorse.BloodHorseRepository
import java.util.concurrent.ConcurrentHashMap
import org.springframework.stereotype.Repository

/**
 * In-Memory な BloodHorseRepository 実装。PoC 用途。
 *
 * 永続化層の代わりとして ConcurrentHashMap で保持する。アプリ再起動でデータは消える。
 */
@Repository
class InMemoryBloodHorseRepository : BloodHorseRepository {
    private val store = ConcurrentHashMap<BloodHorseId, BloodHorse>()

    override fun findById(id: BloodHorseId): BloodHorse? = store[id]

    override fun save(bloodHorse: BloodHorse): BloodHorse {
        store[bloodHorse.id] = bloodHorse
        return bloodHorse
    }
}
