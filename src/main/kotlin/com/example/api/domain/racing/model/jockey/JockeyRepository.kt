package com.example.api.domain.racing.model.jockey

import com.example.api.domain.shared.WorldId
import org.jmolecules.ddd.annotation.Repository

/**
 * ジョッキーの永続化を担うポート
 *
 * ドメイン層はこのインターフェースのみを参照する。実装は infrastructure 層に置く。
 *
 * 全ての口が [WorldId] を要求する（#704 / ADR-0067）。データは世界（セーブデータ＝テナント）ごとに閉じており、 集約自身は世界を知らないため、スコープは引数で運ぶ。
 */
@Repository
interface JockeyRepository {
    /** 指定の世界の中から同姓同名のジョッキーを検索する。その世界に無ければ null。 */
    fun findByFullName(worldId: WorldId, firstName: String, lastName: String): Jockey?

    /**
     * 指定の世界の中からジョッキーを ID で引く。その世界に無ければ null。
     *
     * 書き込みユースケースが**自分が作った集約を読み直す**ための口（冪等キーによる再送の再生。ADR-0072）。
     * 読み取り経路（`JockeyQueries`）とは別物で、ADR-0031 が戒める「read 用途で write ポートに finder を 生やす」には当たらない。
     */
    fun findById(worldId: WorldId, id: JockeyId): Jockey?

    /** ジョッキーを指定の世界に永続化する */
    fun save(worldId: WorldId, jockey: Jockey): Jockey
}
