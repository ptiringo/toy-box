package com.example.api.domain.horseracing.model.racing

import org.jmolecules.ddd.annotation.Repository

/**
 * 競走馬登録（[RacingRegistration]）の永続化を担うポート。
 *
 * ドメイン層はこのインターフェースのみを参照する。実装は infrastructure 層に置く。
 */
@Repository
interface RacingRegistrationRepository {
    /** 競走馬登録IDで検索する。存在しなければ null。 */
    fun findById(id: RacingRegistrationId): RacingRegistration?

    /** 競走馬登録を永続化する。 */
    fun save(racingRegistration: RacingRegistration): RacingRegistration
}
