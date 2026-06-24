package com.example.api.infrastructure.studbook.breeding

import com.example.api.domain.studbook.model.breeding.BreedingRegistrationId
import com.example.api.domain.studbook.model.breeding.BreedingResult
import com.example.api.domain.studbook.model.breeding.BreedingResultId
import com.example.api.domain.studbook.model.breeding.BreedingResultRepository
import java.time.Year
import java.util.concurrent.ConcurrentHashMap
import org.springframework.stereotype.Repository

/**
 * In-Memory な BreedingResultRepository 実装。PoC 用途。
 *
 * 永続化層の代わりとして ConcurrentHashMap で保持する。アプリ再起動でデータは消える。
 */
@Repository
class InMemoryBreedingResultRepository : BreedingResultRepository {
    private val store = ConcurrentHashMap<BreedingResultId, BreedingResult>()

    override fun findById(id: BreedingResultId): BreedingResult? = store[id]

    override fun findByBreedingRegistrationIdAndBreedingYear(
        breedingRegistrationId: BreedingRegistrationId,
        breedingYear: Year,
    ): BreedingResult? =
        store.values.firstOrNull {
            it.breedingRegistrationId == breedingRegistrationId && it.breedingYear == breedingYear
        }

    override fun save(breedingResult: BreedingResult): BreedingResult {
        store[breedingResult.id] = breedingResult
        return breedingResult
    }
}
