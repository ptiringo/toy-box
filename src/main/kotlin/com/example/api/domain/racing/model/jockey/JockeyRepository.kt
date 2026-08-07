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

    /** ジョッキーを指定の世界に永続化する */
    fun save(worldId: WorldId, jockey: Jockey): Jockey
}
