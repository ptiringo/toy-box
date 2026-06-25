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
interface BreedingRegistrationSpringDataRepository : CrudRepository<BreedingRegistrationRow, UUID>
