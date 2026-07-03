-- 繁殖成績報告書（様式第14号）の年次提出（#455）。提出日を breeding_result にフラット化して追加する（ADR-0043）。
-- 期限（翌年5/31、登録規程第25条）超過かどうかは繁殖年からの導出値のため保存しない。
-- 「提出は分娩結果（成績）確定後」（第25条ただし書きの裏返し）の不変条件を CHECK でスキーマ側にも強制する。
-- 既存行があるテーブルへの CHECK 追加はフルスキャン検証で書き込みをブロックしうるため
-- NOT VALID で新規行への強制を即時開始し、VALIDATE CONSTRAINT で既存行を後追い検証する 2 段階で行う（V6 と同様）。
-- timeout は squawk（require-timeout-settings）に従い設定する。Flyway は各マイグレーションを 1 トランザクションで
-- 適用するため、SET LOCAL でこのマイグレーション内に閉じる。
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '5min';

ALTER TABLE breeding_result ADD COLUMN report_submitted_on DATE;

COMMENT ON COLUMN breeding_result.report_submitted_on IS '繁殖成績報告書（様式第14号）の提出日（未提出は NULL）';

ALTER TABLE breeding_result
ADD CONSTRAINT chk_breeding_result_report_needs_outcome
CHECK (report_submitted_on IS NULL OR outcome_type IS NOT NULL) NOT VALID;

ALTER TABLE breeding_result
VALIDATE CONSTRAINT chk_breeding_result_report_needs_outcome;
