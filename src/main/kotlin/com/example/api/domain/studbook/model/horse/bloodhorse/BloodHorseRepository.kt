package com.example.api.domain.studbook.model.horse.bloodhorse

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

    /**
     * 複数の軽種馬IDをまとめて検索する。
     *
     * 父・母の存在確認のように複数IDを引き当てる場面で、1件ずつの逐次 lookup（永続化層では直列往復になる）を 1 回にまとめるためのポート。見つかった分だけを ID をキーにした
     * [Map] で返す（存在しないIDはキーに現れない）。
     */
    fun findAllById(ids: Set<BloodHorseId>): Map<BloodHorseId, BloodHorse>

    /** 軽種馬を永続化する。 */
    fun save(bloodHorse: BloodHorse): BloodHorse
}
