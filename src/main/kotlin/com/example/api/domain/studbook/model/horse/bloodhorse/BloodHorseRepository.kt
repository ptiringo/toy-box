package com.example.api.domain.studbook.model.horse.bloodhorse

import com.example.api.domain.shared.UpdateConflict
import com.example.api.domain.shared.WorldId
import com.github.michaelbull.result.Result
import org.jmolecules.ddd.annotation.Repository

/**
 * 軽種馬（[BloodHorse]）の永続化を担うポート。
 *
 * ドメイン層はこのインターフェースのみを参照する。実装は infrastructure 層に置く。 父・母として参照する馬の取得や、血統登録で誕生した馬の保存に用いる。
 *
 * 全ての口が [WorldId] を要求する（#704 / ADR-0067）。データは世界（セーブデータ＝テナント）ごとに閉じており、 集約自身は世界を知らないため、スコープは引数で運ぶ。
 */
@Repository
interface BloodHorseRepository {
    /** 指定の世界の中から軽種馬IDで検索する。その世界に無ければ null。 */
    fun findById(worldId: WorldId, id: BloodHorseId): BloodHorse?

    /**
     * 指定の世界の中から複数の軽種馬IDをまとめて検索する。
     *
     * 父・母の存在確認のように複数IDを引き当てる読み取り専用の場面で、1件ずつの逐次 lookup（永続化層では 直列往復になる）を 1 回にまとめるためのポート。 見つかった分だけを ID
     * をキーにした [Map] で返す（存在しないIDと他の世界のIDはキーに現れない）。
     */
    fun findAllById(worldId: WorldId, ids: Set<BloodHorseId>): Map<BloodHorseId, BloodHorse>

    /**
     * 軽種馬を指定の世界に永続化する。
     *
     * 集約の [BloodHorse.version] が null なら insert、非 null なら楽観ロック付き update になる （Spring Data JDBC の
     * version 判別）。update が読み取り時点から他の更新と競合していた （または行が並行削除されていた）場合は [UpdateConflict] を返す。
     */
    fun save(worldId: WorldId, bloodHorse: BloodHorse): Result<BloodHorse, UpdateConflict>

    /** 指定の世界の中で、その馬名が既に他の軽種馬に付与されているかを判定する（馬名の一意性照合用）。 */
    fun existsByName(worldId: WorldId, name: HorseName): Boolean
}
