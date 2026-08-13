package com.example.api.infrastructure.shared

import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getOrThrow

/**
 * 検証済みで保存された VO 値を復元時に取り出すヘルパー。
 *
 * `create` が `Result` を返す VO を、DB 由来の trusted データとして失敗しない前提で取り出す（Err は復元データ破損を示す
 * `IllegalStateException`）。書き込み側（集約の再構成）と読み取り側（View の直組み）の双方で要るため、 どちらのパッケージにも属さない
 * `infrastructure.shared`（共有カーネル。コンテキストに属さない）に置く。
 *
 * 例外送出が許されるのは infrastructure リングであることが前提（domain / application は `Result` を返す。
 * error-handling.md）。したがって本ヘルパーを内側のリングから使ってはならない（`internal` はモジュール内に 閉じるだけでリングは縛らないため、依存方向は
 * ArchUnit の onion ルールが担保する）。
 */
internal fun <V, E> Result<V, E>.orThrow(): V = getOrThrow {
    IllegalStateException("永続化された値の復元に失敗しました: $it")
}
