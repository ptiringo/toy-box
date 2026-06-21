package com.example.api.domain.horseracing.model.breeding

import org.jmolecules.ddd.annotation.Repository

/**
 * 種牡馬登録（[StallionRegistration]）の永続化を担うポート。
 *
 * ドメイン層はこのインターフェースのみを参照する。実装は infrastructure 層に置く。
 * 種牡馬登録の成立時の保存や、種付記録（recordCovering）等で参照する種牡馬登録の取得に用いる。
 */
@Repository
interface StallionRegistrationRepository {
    /** 種牡馬登録IDで検索する。存在しなければ null。 */
    fun findById(id: StallionRegistrationId): StallionRegistration?

    /** 種牡馬登録を永続化する。 */
    fun save(stallionRegistration: StallionRegistration): StallionRegistration
}
