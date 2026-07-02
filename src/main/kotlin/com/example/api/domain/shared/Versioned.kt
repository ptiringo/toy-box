package com.example.api.domain.shared

/**
 * 永続化済み集約と楽観ロック version の封筒。
 *
 * 集約そのものは永続化メタデータ（version）を持たない方針（ADR-0027）のため、リポジトリポートの `findById` が本封筒で読み取り時点の version
 * を返し、`update` まで運ぶ。これにより読んだ時点からの 並行更新を楽観ロックとして検出できる。[StateTransition]（集約 + イベント）と同型の汎用キャリアで、
 * jMolecules アノテーションは付けない。
 *
 * [version] は不透明トークンとして扱い、業務判断（分岐・比較）には使わないこと。
 */
data class Versioned<out T>(val value: T, val version: Long) {
    /** 集約だけを写像し、version を引き継いだ新しい封筒を返す（読み取り時点の version を更新まで運ぶ）。 */
    fun <R> map(f: (T) -> R): Versioned<R> = Versioned(f(value), version)
}

/**
 * update が読み取り時点から他の更新と競合した（または対象行が並行削除されていた）ことを表す。
 *
 * リポジトリポートの `update` の失敗側。ユースケースは自分のエラー型（`〜UseCaseError` の `ConcurrentModification` バリアント等）へ wrap
 * し、Controller は 409 Conflict に描画する。
 */
data object UpdateConflict
