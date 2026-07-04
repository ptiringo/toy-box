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
    /** 同一種牡馬（繁殖登録）・同一種付年の種付成績報告を検索する。 */
    fun findByStallionBreedingRegistrationIdAndCoveringYear(
        stallionBreedingRegistrationId: UUID,
        coveringYear: Int,
    ): CoveringReportRow?
}
