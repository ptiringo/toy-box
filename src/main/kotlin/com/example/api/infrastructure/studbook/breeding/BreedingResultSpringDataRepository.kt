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
    /** 指定の世界の中で、同一繁殖牝馬（繁殖登録）・同一繁殖年の年次成績を検索する。 */
    fun findByWorldIdAndBreedingRegistrationIdAndBreedingYear(
        worldId: UUID,
        breedingRegistrationId: UUID,
        breedingYear: Int,
    ): BreedingResultRow?

    /**
     * 指定の世界の中から主キーで引く。
     *
     * `CrudRepository.findById` は世界を絞れないため使わない（他人の世界の行を掴めてしまう）。
     */
    fun findByWorldIdAndId(worldId: UUID, id: UUID): BreedingResultRow?
}
