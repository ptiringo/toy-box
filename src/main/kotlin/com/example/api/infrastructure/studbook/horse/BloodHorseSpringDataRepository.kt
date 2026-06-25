package com.example.api.infrastructure.studbook.horse

import java.util.UUID
import org.springframework.data.repository.CrudRepository

/**
 * Spring Data JDBC が実装を生成する [BloodHorseRow] の CRUD リポジトリ（ADR-0027）。
 *
 * これは infrastructure 内部の永続化詳細であり、ドメインポート
 * [com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseRepository] とは別物。
 * ドメインポートの実装は本リポジトリを委譲先に持つアダプタ [JdbcBloodHorseRepository] が担う。
 */
interface BloodHorseSpringDataRepository : CrudRepository<BloodHorseRow, UUID>
