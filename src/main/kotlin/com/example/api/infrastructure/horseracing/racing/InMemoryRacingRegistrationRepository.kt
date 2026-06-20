package com.example.api.infrastructure.horseracing.racing

import com.example.api.domain.horseracing.model.racing.RacingRegistration
import com.example.api.domain.horseracing.model.racing.RacingRegistrationId
import com.example.api.domain.horseracing.model.racing.RacingRegistrationRepository
import java.util.concurrent.ConcurrentHashMap
import org.springframework.stereotype.Repository

/**
 * [RacingRegistrationRepository] のインメモリ実装（PoC 用途）。
 *
 * アプリケーション再起動でデータは失われる。永続化層の技術選定は別途行う（issue #338）。
 */
@Repository
class InMemoryRacingRegistrationRepository : RacingRegistrationRepository {
    private val store = ConcurrentHashMap<RacingRegistrationId, RacingRegistration>()

    override fun findById(id: RacingRegistrationId): RacingRegistration? = store[id]

    override fun save(racingRegistration: RacingRegistration): RacingRegistration {
        store[racingRegistration.id] = racingRegistration
        return racingRegistration
    }
}
