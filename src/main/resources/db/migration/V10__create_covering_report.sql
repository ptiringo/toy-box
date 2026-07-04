-- covering_report テーブル（#540）。種付成績報告書（様式第13号）の年次提出記録。
-- スキーマは H2(PostgreSQL 互換モード) と PostgreSQL の双方で適用可能な構文に保つ。
-- 識別子は外部採番の UUIDv7（ADR-0005）をアプリ側で採番して渡す（DB 採番ではない）。
-- 報告の内容（雌馬ごとの明細・総括表）は breeding_result から導出できるため保持せず、
-- 提出の事実（種牡馬×種付年・提出日）のみを持つ。期限（当年9/30）超過かは導出値のため保存しない。
-- 「種牡馬×種付年で提出は一度」の集合制約はドメインサービスが検証するが、read-then-insert の
-- 並行競合（#532）に備え UNIQUE 制約を backstop として張る（新規テーブルなので安全に張れる）。
-- version は楽観ロック兼「新規 insert 判定」用の列（V9 以降の規約で NOT NULL）。
-- timeout は squawk（require-timeout-settings）に従い設定する。Flyway は各マイグレーションを
-- 1 トランザクションで適用するため、SET LOCAL でこのマイグレーション内に閉じる（V8 と同様）。
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '5min';

CREATE TABLE covering_report (
    id UUID NOT NULL PRIMARY KEY,
    stallion_breeding_registration_id UUID NOT NULL,
    covering_year INTEGER NOT NULL,
    submitted_on DATE NOT NULL,
    version BIGINT NOT NULL, -- noqa: RF04
    CONSTRAINT uq_covering_report_stallion_year
    UNIQUE (stallion_breeding_registration_id, covering_year)
);

COMMENT ON TABLE covering_report IS '種付成績報告書（様式第13号）の年次提出記録（種牡馬×種付年）';
COMMENT ON COLUMN covering_report.id IS '種付成績報告ID（外部採番の UUIDv7）';
COMMENT ON COLUMN covering_report.stallion_breeding_registration_id IS '提出した種牡馬の繁殖登録ID';
COMMENT ON COLUMN covering_report.covering_year IS '種付年（java.time.Year の int 値。報告対象年）';
COMMENT ON COLUMN covering_report.submitted_on IS '提出日（日本の暦日）。期限（当年9/30）超過かは導出値のため保存しない';
COMMENT ON COLUMN covering_report.version IS '楽観ロック兼 insert 判定用の version';
