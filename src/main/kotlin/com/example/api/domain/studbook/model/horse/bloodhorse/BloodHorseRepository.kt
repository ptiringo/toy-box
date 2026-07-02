package com.example.api.domain.studbook.model.horse.bloodhorse

import com.example.api.domain.shared.UpdateConflict
import com.example.api.domain.shared.Versioned
import com.github.michaelbull.result.Result
import org.jmolecules.ddd.annotation.Repository

/**
 * 軽種馬（[BloodHorse]）の永続化を担うポート。
 *
 * ドメイン層はこのインターフェースのみを参照する。実装は infrastructure 層に置く。 父・母として参照する馬の取得や、血統登録で誕生した馬の保存に用いる。
 */
@Repository
interface BloodHorseRepository {
    /** 軽種馬IDで検索する。存在しなければ null。更新に使う楽観ロック version を [Versioned] で同梱して返す。 */
    fun findById(id: BloodHorseId): Versioned<BloodHorse>?

    /**
     * 複数の軽種馬IDをまとめて検索する。
     *
     * 父・母の存在確認のように複数IDを引き当てる読み取り専用の場面で、1件ずつの逐次 lookup（永続化層では 直列往復になる）を 1 回にまとめるためのポート。更新には使わないため
     * [Versioned] を同梱しない。 見つかった分だけを ID をキーにした [Map] で返す（存在しないIDはキーに現れない）。
     */
    fun findAllById(ids: Set<BloodHorseId>): Map<BloodHorseId, BloodHorse>

    /** 軽種馬を新規に永続化する（insert 専用）。既存集約の更新（馬名登録等）は [update] を使う。 */
    fun save(bloodHorse: BloodHorse): BloodHorse

    /**
     * 既存の軽種馬を楽観ロック付きで更新する（馬名登録の反映等）。
     *
     * [Versioned.version]（読み取り時点の version）が現在行と一致するときだけ更新し、version を進めた
     * 新しい封筒を返す。読み取り後に他の更新が入っていた（または行が消えていた）場合は [UpdateConflict]。
     */
    fun update(versioned: Versioned<BloodHorse>): Result<Versioned<BloodHorse>, UpdateConflict>

    /** 指定の馬名が既に他の軽種馬に付与されているかを判定する（馬名の一意性照合用）。 */
    fun existsByName(name: HorseName): Boolean
}
