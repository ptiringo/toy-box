package com.example.api.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import org.jetbrains.kotlin.psi.KtCatchClause

/**
 * 永続化アダプタ（`infrastructure`）で楽観ロックの競合を `catch` で受け取ることを禁止する detekt カスタムルール。
 *
 * Spring Data JDBC の `save` は競合を `OptimisticLockingFailureException`
 * で知らせるが、`SimpleJdbcRepository.save` 自身が `@Transactional`
 * を持ちユースケースのトランザクションに**参加**するため、内側で例外が起きた時点で global rollback-only がマークされる。捕まえて
 * `Err(UpdateConflict)` に写しても外側のコミットが `UnexpectedRollbackException` になり、409 のつもりが 500 になる（#739
 * / #867）。
 *
 * 正しい形は `infrastructure.shared` の `lockRowIfVersionMatches` で対象行を `FOR UPDATE` ロックして版を突き合わせ、
 * **例外を起こさせない**こと（`.claude/rules/architecture.md`）。この誤りは書いた時点では緑になる（リポジトリを直呼びする
 * 契約テストでは外側にコミットすべきトランザクションが無く、穴が現れない）ため、レビューではなくルールで止める。
 *
 * 検出対象は `catch` 節の型が `OptimisticLockingFailureException` で終わるもの。detekt は型解決なしの PSI で
 * 動かしているため判定はソース上のテキストで行い、完全修飾表記とサブタイプ（`ObjectOptimisticLockingFailureException`）を 末尾一致で拾う。既知の限界:
 * `DataAccessException` や `Exception` のような広い型で受ける書き方は検出できない （レビュー担保）。テストコードは detekt 設定の `excludes`
 * で対象から外す。
 */
class NoCatchOptimisticLockingFailure(config: Config) :
    Rule(
        config,
        "永続化アダプタで楽観ロックの競合を catch しないこと。" +
            "捕まえても外側のコミットが UnexpectedRollbackException になるため、" +
            "lockRowIfVersionMatches で版を突き合わせて例外を起こさせない。",
    ) {
    override fun visitCatchSection(catchClause: KtCatchClause) {
        super.visitCatchSection(catchClause)

        val packageName = catchClause.containingKtFile.packageFqName.asString()
        if (
            packageName != TARGET_PACKAGE_PREFIX &&
                !packageName.startsWith("$TARGET_PACKAGE_PREFIX.")
        ) {
            return
        }

        val caughtType = catchClause.catchParameter?.typeReference?.text?.substringAfterLast('.')
        if (caughtType?.endsWith(OPTIMISTIC_LOCKING_EXCEPTION) == true) {
            report(
                Finding(
                    Entity.from(catchClause),
                    "楽観ロックの競合を catch しないこと。save はユースケースのトランザクションに参加するため、" +
                        "Err(UpdateConflict) に写しても外側のコミットが " +
                        "UnexpectedRollbackException になる（409 が 500 になる）。" +
                        "update 経路は infrastructure.shared の lockRowIfVersionMatches で版を突き合わせること" +
                        "（.claude/rules/architecture.md）。",
                )
            )
        }
    }

    private companion object {
        /** 検査対象パッケージ（この誤りが成立するのは永続化アダプタだけ）。 */
        const val TARGET_PACKAGE_PREFIX = "com.example.api.infrastructure"

        /** 楽観ロック競合を表す例外型の末尾。完全修飾表記とサブタイプを末尾一致で拾う。 */
        const val OPTIMISTIC_LOCKING_EXCEPTION = "OptimisticLockingFailureException"
    }
}
