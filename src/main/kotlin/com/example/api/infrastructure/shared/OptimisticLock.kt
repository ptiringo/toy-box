package com.example.api.infrastructure.shared

import java.util.UUID
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.transaction.support.TransactionSynchronizationManager

/**
 * 楽観ロックの競合を**例外にせず**判定するために、更新対象の行をロックして版を突き合わせる（#867）。
 *
 * Spring Data JDBC の `save` は競合を `OptimisticLockingFailureException` で知らせるが、その口は
 * ユースケースのトランザクション境界の内側では使えない。`SimpleJdbcRepository.save` 自身が `@Transactional` を
 * 持ち、呼び出し側のトランザクションに**参加**する形で走るため、内側で例外が起きた時点で global rollback-only が マークされる。捕まえて
 * `Err(UpdateConflict)` に写しても**外側のコミットが `UnexpectedRollbackException` になる** （＝409 のつもりが 500）。UNIQUE
 * 違反を `ON CONFLICT DO NOTHING` で例外にしないのと同じ理由で、こちらも 例外を起こさせずに結果だけを見る。
 *
 * 行を `FOR UPDATE` でロックしたうえで版が一致していれば、続く `save` の UPDATE は必ず 1 行に当たる （並行する更新はロック解放まで待たされる）。つまり
 * `save` が競合で例外を投げる余地そのものを無くす。
 *
 * @param qualifiedTable `スキーマ名.テーブル名`。呼び出し側が定数で渡す（外部入力を渡さないこと）
 * @return 版が一致して更新してよければ true、競合（版の不一致・行の並行削除）なら false
 */
internal fun JdbcClient.lockRowIfVersionMatches(
    qualifiedTable: String,
    id: UUID,
    version: Long,
): Boolean {
    // ロックはトランザクションの終了まで保持されて初めて直列化になる。境界の外から呼ばれると FOR UPDATE が
    // 即座に解放されて防御が無症状で消えるため、誤用を例外として顕在化させる（JdbcWorldRepository と同じ理由）。
    check(TransactionSynchronizationManager.isActualTransactionActive()) {
        "$qualifiedTable の更新はトランザクション内で行うこと（境界の外では行ロックが即座に解放される）"
    }
    val current =
        sql("SELECT version FROM $qualifiedTable WHERE id = :id FOR UPDATE")
            .param("id", id)
            .query(Long::class.java)
            .optional()
            .orElse(null)
    return current == version
}
