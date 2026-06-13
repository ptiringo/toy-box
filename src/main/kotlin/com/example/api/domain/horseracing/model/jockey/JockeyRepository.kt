package com.example.api.domain.horseracing.model.jockey

import org.jmolecules.ddd.annotation.Repository

/**
 * ジョッキーの永続化を担うポート
 *
 * ドメイン層はこのインターフェースのみを参照する。実装は infrastructure 層に置く。
 */
@Repository
interface JockeyRepository {
    /** 同姓同名のジョッキーを検索する。存在しなければ null。 */
    fun findByFullName(firstName: String, lastName: String): Jockey?

    /** ジョッキーを永続化する */
    fun save(jockey: Jockey): Jockey
}
