-- breeding_registration の供用停止（BreedingRetirement）の共在 CHECK を遡及追加する（#522 / ADR-0043）。
-- 不変条件は「retirement_reason / retirement_occurred_on の両方 NULL（供用中）か両方 NOT NULL（供用停止済み）」。
-- V2 作成当時は H2(PostgreSQL 互換モード) との両対応が必要で見送られていたが、
-- #451 の H2 全面脱却（ADR-0044、実 PostgreSQL 一本化）で前提が消えたため追加する。
-- 既存行があるテーブルへの CHECK 追加はフルスキャン検証で書き込みをブロックしうるため 2 段階で行う:
-- NOT VALID で新規行への強制を即時開始（既存行は未検証・ロック最小）し、
-- VALIDATE CONSTRAINT で既存行を後追い検証する（SHARE UPDATE EXCLUSIVE・書き込みを止めない）。
-- timeout は squawk（require-timeout-settings）に従い設定する。Flyway は各マイグレーションを
-- 1 トランザクションで適用するため、SET LOCAL でこのマイグレーション内に閉じる
-- （ロック待ちで他セッションを長時間塞がない・想定外の長時間実行を打ち切る）。
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '5min';

ALTER TABLE breeding_registration
ADD CONSTRAINT chk_breeding_registration_retirement_coexistence
CHECK (
    (retirement_reason IS NULL AND retirement_occurred_on IS NULL)
    OR (retirement_reason IS NOT NULL AND retirement_occurred_on IS NOT NULL)
) NOT VALID;

ALTER TABLE breeding_registration
VALIDATE CONSTRAINT chk_breeding_registration_retirement_coexistence;
