package com.example.api.application.shared

import com.example.api.domain.shared.Permission

/**
 * 権限不足でユースケースを実行できなかったことを表すマーカー。
 *
 * 各ユースケースの `sealed interface XxxUseCaseError` は同一パッケージのサブタイプしか持てないため、 単一の共通 `Forbidden`
 * 型を全ユースケースで共有することはできない。各ユースケースが自前の `Forbidden` を持ち、このマーカーを併せて実装することで、Controller 側は横串（403 への写像）を
 * 一箇所に書ける（error-handling.md の「重複が出た段階で共通親を切り出す」に沿う）。
 */
interface AuthorizationError {
    /** 実行に必要だった権限。 */
    val permission: Permission
}
