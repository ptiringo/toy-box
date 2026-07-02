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
     * ドメイン側は楽観ロックの version を持たない（オニオン規約上 Spring 依存を載せられず、永続化メタデータを ドメインへ漏らさない方針）。そのため version は常に
     * null となり Spring Data JDBC は insert と判定する。[Jockey] には更新の語彙（状態遷移メソッド）が現状無いため insert のみ
     * を扱う。更新が必要になったら ADR-0047 の Versioned 封筒方式で update を追加する。
     */
    private fun Jockey.toRow(): JockeyRow =
        JockeyRow(id = id.value, firstName = firstName, lastName = lastName)
}
