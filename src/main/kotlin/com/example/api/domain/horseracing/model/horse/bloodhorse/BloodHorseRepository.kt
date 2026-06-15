package com.example.api.domain.horseracing.model.horse.bloodhorse

import org.jmolecules.ddd.annotation.Repository

/**
 * 軽種馬（[BloodHorse]）の永続化を担うポート。
 *
 * ドメイン層はこのインターフェースのみを参照する。実装は infrastructure 層に置く。 父・母として参照する馬の取得や、血統登録で誕生した馬の保存に用いる。
 */
@Repository
interface BloodHorseRepository {
    /** 軽種馬IDで検索する。存在しなければ null。 */
    fun findById(id: BloodHorseId): BloodHorse?

    /** 軽種馬を永続化する。 */
    fun save(bloodHorse: BloodHorse): BloodHorse
}
