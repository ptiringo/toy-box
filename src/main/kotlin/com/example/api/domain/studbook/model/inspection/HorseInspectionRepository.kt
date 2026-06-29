package com.example.api.domain.studbook.model.inspection

import org.jmolecules.ddd.annotation.Repository

/**
 * 審査集約 [HorseInspection] の永続化ポート。
 *
 * 実装は infrastructure 層（Spring Data JDBC アダプタ）が担う。血統登録は確定済み審査を [save] で永続化してから 消費し、復元は [findById]
 * で行う。
 */
@Repository
interface HorseInspectionRepository {
    /** ID で審査を取得する。存在しなければ null。 */
    fun findById(id: HorseInspectionId): HorseInspection?

    /** 審査を保存し、保存後の集約を返す。 */
    fun save(inspection: HorseInspection): HorseInspection
}
