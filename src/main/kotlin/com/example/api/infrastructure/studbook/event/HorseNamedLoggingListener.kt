package com.example.api.infrastructure.studbook.event

import com.example.api.domain.studbook.model.horse.bloodhorse.HorseNamed
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * [HorseNamed] を購読してログ出力する参考実装リスナ。
 *
 * イベント購読は REST / persistence / MCP と並ぶアダプタの一形態として infrastructure 層に置く。 `AFTER_COMMIT`
 * により、発行元トランザクションのコミットが確定した場合にのみ同期実行される （ロールバック時は配送されない =
 * publish-after-commit）。リスナが走る時点でコミットは取り消せないため、 ここに置けるのは失敗しても本体の書き込みに影響しない処理（通知・ログ等）に限る。決定経緯は
 * ADR-0050。
 */
@Component
class HorseNamedLoggingListener {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onHorseNamed(event: HorseNamed) {
        logger.info("ドメインイベント受信: {}", event)
    }

    private companion object {
        private val logger = LoggerFactory.getLogger(HorseNamedLoggingListener::class.java)
    }
}
