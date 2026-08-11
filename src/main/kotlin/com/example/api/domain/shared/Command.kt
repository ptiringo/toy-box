package com.example.api.domain.shared

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import java.time.Clock
import java.time.Instant

/** 冪等キー（[Idempotency.key]）の形式不備。 */
sealed interface IdempotencyKeyValidationError {
    /** キーがブランク（空文字列・空白のみ）。 */
    data object Blank : IdempotencyKeyValidationError

    /** キーが [Idempotency.MAX_KEY_LENGTH] 文字を超える。 */
    data object TooLong : IdempotencyKeyValidationError
}

/**
 * 再送を識別する冪等キーと、リクエストの同一性を表す指紋（ADR-0072 / #750）。
 *
 * 指紋はリクエスト DTO の SHA-256（hex）。同じキーが別内容のリクエストで使い回されたことを検出するために持つ。
 * 算出はアダプタ（controller）の責務。直列化フォーマット（DTO をどうバイト列へ落とすか）は転送レイヤの関心であり ドメイン /
 * アプリケーション層の業務ロジックには属さないため、ここには持ち込まない（機械強制のゲートがあるからではない）。
 *
 * 不変条件（非ブランク・[MAX_KEY_LENGTH] 文字以内）を満たした上で生成するために、コンストラクタは private にして [Idempotency.create]
 * でのみ生成する。
 *
 * jMolecules のアノテーションは付けない。[Command] と同じく、ドメインのビルディングブロックではない 汎用キャリアだから。
 *
 * @property key クライアントが付けた冪等キー
 * @property requestFingerprint リクエスト DTO の SHA-256（hex 64 文字）
 */
class Idempotency private constructor(val key: String, val requestFingerprint: String) {
    companion object {
        /**
         * [key] の最大文字数。`shared.idempotency_record.idempotency_key` の列幅
         * （`VARCHAR(255)`、`V21__create_shared_idempotency_record.sql`）と一致させること。 ずれると、ここを通過したキーが
         * INSERT 時に PostgreSQL の 22001（value too long）で落ちる。
         */
        const val MAX_KEY_LENGTH = 255

        /**
         * [key] が非ブランクかつ [MAX_KEY_LENGTH] 文字以内であることを検証して [Idempotency] を生成する。
         *
         * @return 生成された [Idempotency]、または不変条件違反を表す [IdempotencyKeyValidationError]
         */
        fun create(
            key: String,
            requestFingerprint: String,
        ): Result<Idempotency, IdempotencyKeyValidationError> =
            when {
                key.isBlank() -> Err(IdempotencyKeyValidationError.Blank)
                key.length > MAX_KEY_LENGTH -> Err(IdempotencyKeyValidationError.TooLong)
                else -> Ok(Idempotency(key, requestFingerprint))
            }
    }
}

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
