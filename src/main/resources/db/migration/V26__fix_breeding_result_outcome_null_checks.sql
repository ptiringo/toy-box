-- 繁殖成績の分娩結果まわりの CHECK 2 本が、区分 outcome_type が NULL の行を素通りしていたので張り直す（#983）。
--
-- outcome_type は nullable（種付済み・未報告の年は NULL）。`outcome_type = 'LIVE_FOAL'` のような比較は
-- NULL のとき FALSE ではなく NULL を返し、OR 全体も NULL になる。CHECK は NULL を違反とみなさないため、
-- 次の不整合行が DB 単独では拒否されていなかった（契約テストで実測）。
--
-- - chk_breeding_result_foaling_date: 区分が NULL なのに分娩日を持つ行（分娩日を持てるのは生産だけ）
-- - chk_breeding_result_outcome_covering: 種付なしなのに区分が NULL の行（種付せずの年は NOT_COVERED）
--
-- V24 で covering_type に施したのと同じく、比較の前に IS NOT NULL を置く。既存行のあるテーブルへの
-- CHECK 追加なので NOT VALID で張り、VALIDATE は V27 に分離する（ADR-0052）。
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '5min';

ALTER TABLE studbook.breeding_result
DROP CONSTRAINT chk_breeding_result_foaling_date;

ALTER TABLE studbook.breeding_result
ADD CONSTRAINT chk_breeding_result_foaling_date CHECK (
    outcome_foaling_date IS NULL
    OR (outcome_type IS NOT NULL AND outcome_type = 'LIVE_FOAL')
) NOT VALID;

ALTER TABLE studbook.breeding_result
DROP CONSTRAINT chk_breeding_result_outcome_covering;

ALTER TABLE studbook.breeding_result
ADD CONSTRAINT chk_breeding_result_outcome_covering CHECK (
    (
        covering_date IS NULL
        AND outcome_type IS NOT NULL
        AND outcome_type = 'NOT_COVERED'
    )
    OR (
        covering_date IS NOT NULL
        AND (outcome_type IS NULL OR outcome_type <> 'NOT_COVERED')
    )
) NOT VALID;
