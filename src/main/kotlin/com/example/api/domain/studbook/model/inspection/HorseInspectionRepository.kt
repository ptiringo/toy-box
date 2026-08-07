package com.example.api.domain.studbook.model.inspection

import com.example.api.domain.shared.WorldId
import org.jmolecules.ddd.annotation.Repository

/**
 * 審査集約 [HorseInspection] の永続化ポート。
 *
 * 実装は infrastructure 層（Spring Data JDBC アダプタ）が担う。血統登録は確定済み審査を [save] で永続化してから 消費し、復元は [findById]
 * で行う。
 *
 * 全ての口が [WorldId] を要求する（#704 / ADR-0067）。データは世界（セーブデータ＝テナント）ごとに閉じており、 集約自身は世界を知らないため、スコープは引数で運ぶ。
 */
@Repository
interface HorseInspectionRepository {
    /** 指定の世界の中から ID で審査を取得する。その世界に無ければ null。 */
    fun findById(worldId: WorldId, id: HorseInspectionId): HorseInspection?

    /** 審査を指定の世界に保存し、保存後の集約を返す。 */
    fun save(worldId: WorldId, inspection: HorseInspection): HorseInspection
}
