package com.example.api.application.shared.idempotency

import com.example.api.domain.shared.WorldId
import java.util.UUID

/**
 * 冪等キー 1 件の記録。
 *
 * @property requestFingerprint そのキーを**最初に**確保したリクエストの指紋
 * @property resourceId 成功時に作られたリソースのID。`null` は「前回の試行は結果を残さなかった」を表す
 */
data class IdempotencyRecord(val requestFingerprint: String, val resourceId: UUID?)

/**
 * 冪等キーの記録を保つポート。
 *
 * ドメインの概念ではない（再送というプロトコル上の事象を扱う）ため、jMolecules の `@Repository` を付けた
 * ドメインポートにはせず、読み取りポート（`〜Queries`）と同じく application 層に置く（ADR-0031 と同型）。
 *
 * **呼び出しはユースケースのトランザクション内でのみ意味を持つ**。[claim] が取る行ロックは、そのトランザクションが 終わるまで並行する再送を待たせるためのものだから（ADR-0051
 * / ADR-0072）。
 */
interface IdempotencyStore {
    /**
     * キーの行を確保し、行ロックを取ったうえで現在の記録を返す。
     *
     * 初回は `resourceId` が `null` の行を作って返す。既にあればその行を返す（[requestFingerprint] は 先着のものになる）。**UNIQUE
     * 違反を例外にしない**ため、衝突してもトランザクションは abort しない。
     */
    fun claim(worldId: WorldId, key: String, requestFingerprint: String): IdempotencyRecord

    /** 実処理が成功したときに、結果のリソースIDを記録する。 */
    fun recordResource(worldId: WorldId, key: String, resourceId: UUID)
}
