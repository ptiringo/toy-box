package com.example.api.domain.shared

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
class Command<T>(val payload: T, val issuedAt: Instant)
