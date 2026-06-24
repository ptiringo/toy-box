package com.example.api.domain.studbook.model.breeding

import org.jmolecules.ddd.annotation.Repository

/**
 * 繁殖登録（[BreedingRegistration]）の永続化を担うポート。
 *
 * ドメイン層はこのインターフェースのみを参照する。実装は infrastructure 層に置く。 種付記録（recordCovering）の入力となる繁殖登録の取得や、繁殖登録の成立時の保存に
 * 用いる。
 */
@Repository
interface BreedingRegistrationRepository {
    /** 繁殖登録IDで検索する。存在しなければ null。 */
    fun findById(id: BreedingRegistrationId): BreedingRegistration?

    /** 繁殖登録を永続化する。 */
    fun save(breedingRegistration: BreedingRegistration): BreedingRegistration
}
