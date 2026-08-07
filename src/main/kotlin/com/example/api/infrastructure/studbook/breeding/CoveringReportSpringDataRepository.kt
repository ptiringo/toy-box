package com.example.api.infrastructure.studbook.breeding

import java.util.UUID
import org.springframework.data.repository.CrudRepository

/**
 * Spring Data JDBC が実装を生成する [CoveringReportRow] の CRUD リポジトリ（ADR-0027）。
 *
 * これは infrastructure 内部の永続化詳細であり、ドメインポート
 * [com.example.api.domain.studbook.model.breeding.CoveringReportRepository] とは別物。
 * ドメインポートの実装は本リポジトリを委譲先に持つアダプタ [JdbcCoveringReportRepository] が担う。
 */
interface CoveringReportSpringDataRepository : CrudRepository<CoveringReportRow, UUID> {
    /** 指定の世界の中で、同一種牡馬（繁殖登録）・同一種付年の種付成績報告を検索する。 */
    fun findByWorldIdAndStallionBreedingRegistrationIdAndCoveringYear(
        worldId: UUID,
        stallionBreedingRegistrationId: UUID,
        coveringYear: Int,
    ): CoveringReportRow?

    /**
     * 指定の世界の中から主キーで引く。
     *
     * `CrudRepository.findById` は世界を絞れないため使わない（他人の世界の行を掴めてしまう）。
     */
    fun findByWorldIdAndId(worldId: UUID, id: UUID): CoveringReportRow?
}
