package com.example.api.infrastructure.racing.jockey

import com.example.api.domain.racing.model.jockey.Jockey
import com.example.api.domain.racing.model.jockey.JockeyId
import com.example.api.domain.racing.model.jockey.JockeyRepository

/**
 * [#338 spike] ドメインポート [JockeyRepository] の Spring Data JDBC 実装（ADR-0027）。
 *
 * ドメイン集約 [Jockey] と永続化モデル [JockeyRow] を手書きマッパーで相互変換し、CRUD は [JockeySpringDataRepository]
 * へ委譲する。value class の `JockeyId` ↔ DB `uuid` 列の変換も、別途の Spring Data カスタムコンバータではなく本マッパーが
 * `JockeyId(uuid)` / `id.value` で担う（永続化モデルを分離した帰結。ADR-0027）。
 *
 * spike のため Spring Bean 化（`@Repository`）はしない（既存の `InMemoryJockeyRepository` と DI 衝突するため）。
 * 本番配線（プロファイル分け等）は別イシューに切り出す。
 */
class JdbcJockeyRepository(private val rows: JockeySpringDataRepository) : JockeyRepository {

    override fun findByFullName(firstName: String, lastName: String): Jockey? =
        rows.findByFirstNameAndLastName(firstName, lastName)?.toDomain()

    override fun save(jockey: Jockey): Jockey = rows.save(jockey.toRow()).toDomain()

    /** 永続化モデルからドメイン集約を再構成する（検証・採番なし）。 */
    private fun JockeyRow.toDomain(): Jockey =
        Jockey.reconstitute(JockeyId(id), firstName, lastName)

    /**
     * ドメイン集約を永続化モデルへ写す。
     *
     * ドメイン側は楽観ロックの version を持たない（オニオン規約上 Spring 依存を載せられず、永続化メタデータを ドメインへ漏らさない方針）。そのため version は常に
     * null となり Spring Data JDBC は insert と判定する。 既存行の update（version を進める）は本 spike の範囲外（対処方針は
     * ADR-0027 を参照）。
     */
    private fun Jockey.toRow(): JockeyRow =
        JockeyRow(id = id.value, firstName = firstName, lastName = lastName)
}
