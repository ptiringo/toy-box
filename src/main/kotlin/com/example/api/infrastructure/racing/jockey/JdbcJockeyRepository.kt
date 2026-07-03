package com.example.api.infrastructure.racing.jockey

import com.example.api.domain.racing.model.jockey.Jockey
import com.example.api.domain.racing.model.jockey.JockeyId
import com.example.api.domain.racing.model.jockey.JockeyRepository
import org.springframework.stereotype.Repository

/**
 * ドメインポート [JockeyRepository] の唯一の実装。Spring Data JDBC で永続化する（ADR-0027 / ADR-0030）。
 *
 * ドメイン集約 [Jockey] と永続化モデル [JockeyRow] を手書きマッパーで相互変換し、CRUD は [JockeySpringDataRepository]
 * へ委譲する。value class の `JockeyId` ↔ DB `uuid` 列の変換も、別途の Spring Data カスタムコンバータではなく本マッパーが
 * `JockeyId(uuid)` / `id.value` で担う（永続化モデルを分離した帰結。ADR-0027）。
 *
 * 永続化実装は JDBC 一本に統一し、起動 datasource を H2(dev / Cloud Run) ↔ PostgreSQL(本番) で差し替える方針のため、 InMemory
 * 実装・プロファイル切替は持たない（ADR-0030）。デフォルト（H2・PostgreSQL 互換）でも本クラスが配線される。
 */
@Repository
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
     * [Jockey] には更新の語彙（状態遷移メソッド）が現状無いため、`Entity.version`（既定の `null`）を override せず 常に insert
     * のみを扱う。更新が必要になったら集約に version を override して save 一本方式に乗る（ADR-0047）。
     */
    private fun Jockey.toRow(): JockeyRow =
        JockeyRow(id = id.value, firstName = firstName, lastName = lastName)
}
