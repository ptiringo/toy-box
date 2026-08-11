-- 冪等キー（Idempotency-Key ヘッダ）の記録（ADR-0072 / #750）。
-- 再送の識別だけが目的で、ドメインの概念ではないためコンテキスト別スキーマではなく shared に置く。
--
-- resource_id は NULL 可。NULL は「キーは確保したが、前回の試行は結果を残さなかった（業務エラー等）」を表す。
-- 主キーを (world_id, idempotency_key) にしているのは、これが冪等性の唯一の裁定者だから
-- （ON CONFLICT の衝突対象でもある）。
--
-- timeout は squawk（require-timeout-settings）に従い設定する。
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '5min';

CREATE SCHEMA IF NOT EXISTS shared;

CREATE TABLE shared.idempotency_record (
    world_id UUID NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    resource_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_idempotency_record PRIMARY KEY (world_id, idempotency_key),
    CONSTRAINT fk_idempotency_record_world FOREIGN KEY (world_id)
    REFERENCES iam.world (id) ON DELETE CASCADE
);

COMMENT ON TABLE shared.idempotency_record IS '再送を識別する冪等キーの記録（Idempotency-Key ヘッダ）';
COMMENT ON COLUMN shared.idempotency_record.world_id IS 'キーが属する世界のID（世界の削除で連鎖削除される）';
COMMENT ON COLUMN shared.idempotency_record.idempotency_key IS 'クライアントが付けた冪等キー';
COMMENT ON COLUMN shared.idempotency_record.request_fingerprint IS 'リクエスト本文の SHA-256（hex）';
COMMENT ON COLUMN shared.idempotency_record.resource_id IS '成功時に作られたリソースのID（未成功なら NULL）';
COMMENT ON COLUMN shared.idempotency_record.created_at IS 'キーを確保した時刻';
