package com.example.api.infrastructure.studbook.breeding

import java.util.UUID
import org.springframework.data.repository.CrudRepository

/**
 * Spring Data JDBC が実装を生成する [BreedingRegistrationRow] の CRUD リポジトリ（ADR-0027）。
 *
 * これは infrastructure 内部の永続化詳細であり、ドメインポート
 * [com.example.api.domain.studbook.model.breeding.BreedingRegistrationRepository] とは別物。
 * ドメインポートの実装は本リポジトリを委譲先に持つアダプタ [JdbcBreedingRegistrationRepository] が担う。
 */
interface BreedingRegistrationSpringDataRepository : CrudRepository<BreedingRegistrationRow, UUID> {
    /**
     * 指定の世界の中から主キーで引く。
     *
     * `CrudRepository.findById` は世界を絞れないため使わない（他人の世界の行を掴めてしまう）。世界スコープ化 （#704）以降、読み取りは必ず `world_id`
     * を伴うこの口を通す。
     */
    fun findByWorldIdAndId(worldId: UUID, id: UUID): BreedingRegistrationRow?

    /** 指定の世界の中で、繁殖登録番号（`breeding_registration.registration_number`）が一致する行が存在するか。 登録番号の一意性照合に用いる。 */
    fun existsByWorldIdAndRegistrationNumber(worldId: UUID, registrationNumber: String): Boolean
}
