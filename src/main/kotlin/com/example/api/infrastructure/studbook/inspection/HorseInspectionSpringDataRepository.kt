package com.example.api.infrastructure.studbook.inspection

import java.util.UUID
import org.springframework.data.repository.CrudRepository

/**
 * Spring Data JDBC が実装を生成する [HorseInspectionRow] の CRUD リポジトリ（ADR-0027）。
 *
 * infrastructure 内部の永続化詳細であり、ドメインポート
 * [com.example.api.domain.studbook.model.inspection.HorseInspectionRepository] とは別物。ドメインポートの実装は
 * 本リポジトリを委譲先に持つアダプタ [JdbcHorseInspectionRepository] が担う。
 */
interface HorseInspectionSpringDataRepository : CrudRepository<HorseInspectionRow, UUID> {
    /**
     * 指定の世界の中から主キーで引く。
     *
     * `CrudRepository.findById` は世界を絞れないため使わない（他人の世界の行を掴めてしまう）。世界スコープ化 （#704）以降、読み取りは必ず `world_id`
     * を伴うこの口を通す。
     */
    fun findByWorldIdAndId(worldId: UUID, id: UUID): HorseInspectionRow?
}
