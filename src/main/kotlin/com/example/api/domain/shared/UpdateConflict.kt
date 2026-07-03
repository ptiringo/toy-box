package com.example.api.domain.shared

/**
 * save の update 経路が読み取り時点から他の更新と競合した（または対象行が並行削除されていた）ことを表す。
 *
 * リポジトリポートの `save` の失敗側（[Entity.version] が非 null で Spring Data JDBC が update と判定した場合）。
 * ユースケースは自分のエラー型（`〜UseCaseError` の `ConcurrentModification` バリアント等）へ wrap し、Controller は 409
 * Conflict に描画する（ADR-0047）。
 */
data object UpdateConflict
