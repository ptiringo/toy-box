package com.example.api.infrastructure.studbook.breeding

import java.util.UUID
import org.springframework.data.repository.CrudRepository

/**
 * Spring Data JDBC が実装を生成する [BreedingResultRow] の CRUD リポジトリ（ADR-0027）。
 *
 * これは infrastructure 内部の永続化詳細であり、ドメインポート
 * [com.example.api.domain.studbook.model.breeding.BreedingResultRepository] とは別物。
 * ドメインポートの実装は本リポジトリを委譲先に持つアダプタ [JdbcBreedingResultRepository] が担う。
 */
interface BreedingResultSpringDataRepository : CrudRepository<BreedingResultRow, UUID> {
    /** 同一繁殖牝馬（繁殖登録）・同一繁殖年の年次成績を検索する。 */
    fun findByBreedingRegistrationIdAndBreedingYear(
        breedingRegistrationId: UUID,
        breedingYear: Int,
    ): BreedingResultRow?
}
