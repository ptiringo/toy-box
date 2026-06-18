package com.example.api.domain.shared

import java.time.Clock
import java.time.Instant

/**
 * ドメインコマンドのペイロードに発生時刻メタデータを添える封筒。
 *
 * ペイロード（何をしたいか）と、それがいつ発生したかという横断的メタデータを分離するための薄いラッパー。 各ユースケースの入力 DTO（`〜Command`）をそのまま [payload]
 * に載せ、`Command` 自体はコンテキストに依存しない。
 *
 * @param T 包むドメインコマンドの型
 * @property payload 実行したいドメインコマンド
 * @property issuedAt コマンドが発生した時刻。タイムゾーンに依存しないドメインイベント時刻として [Instant] で保持する
 */
class Command<T>(val payload: T, val issuedAt: Instant) {
    companion object {
        /**
         * 時刻源 [clock] から発生時刻を採取して [payload] を封筒に詰める。
         *
         * `Instant.now()` の直書きを各アダプターに散らさず、注入された [Clock] 経由に一元化するためのファクトリ。 テストでは固定 [Clock] を渡すことで
         * [issuedAt] を決定的にできる。
         */
        fun <T> now(payload: T, clock: Clock): Command<T> = Command(payload, Instant.now(clock))
    }
}
