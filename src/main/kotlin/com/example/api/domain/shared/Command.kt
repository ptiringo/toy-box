package com.example.api.domain.shared

import java.time.Clock
import java.time.Instant

/**
 * 再送を識別する冪等キーと、リクエストの同一性を表す指紋（ADR-0072 / #750）。
 *
 * 指紋はリクエスト本文の SHA-256（hex）。同じキーが別内容のリクエストで使い回されたことを検出するために持つ。 算出はアダプタ（controller）の責務で、application
 * 層は Jackson を import できないためここには持ち込まない。
 *
 * jMolecules のアノテーションは付けない。[Command] と同じく、ドメインのビルディングブロックではない 汎用キャリアだから。
 *
 * @property key クライアントが付けた冪等キー
 * @property requestFingerprint リクエスト本文の SHA-256（hex 64 文字）
 */
class Idempotency(val key: String, val requestFingerprint: String)

/**
 * ドメインコマンドのペイロードに横断的メタデータを添える封筒。
 *
 * ペイロード（何をしたいか）と、それがいつ発生したか・どの再送に属するかという横断的メタデータを分離するための 薄いラッパー。各ユースケースの入力 DTO（`〜Command`）をそのまま
 * [payload] に載せ、`Command` 自体は コンテキストに依存しない。
 *
 * @param T 包むドメインコマンドの型
 * @property payload 実行したいドメインコマンド
 * @property issuedAt コマンドが発生した時刻。タイムゾーンに依存しないドメインイベント時刻として [Instant] で保持する
 * @property idempotency 再送を識別する冪等キー。付いていなければ再送判定を行わない
 */
class Command<T>(val payload: T, val issuedAt: Instant, val idempotency: Idempotency? = null) {
    companion object {
        /**
         * 時刻源 [clock] から発生時刻を採取して [payload] を封筒に詰める。
         *
         * `Instant.now()` の直書きを各アダプターに散らさず、注入された [Clock] 経由に一元化するためのファクトリ。 テストでは固定 [Clock] を渡すことで
         * [issuedAt] を決定的にできる。
         */
        fun <T> now(payload: T, clock: Clock, idempotency: Idempotency? = null): Command<T> =
            Command(payload, Instant.now(clock), idempotency)
    }
}
