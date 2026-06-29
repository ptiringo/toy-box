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
interface HorseInspectionSpringDataRepository : CrudRepository<HorseInspectionRow, UUID>
