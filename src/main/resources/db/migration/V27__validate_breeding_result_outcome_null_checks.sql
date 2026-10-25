-- V26 で NOT VALID として張り直した繁殖成績の分娩結果まわりの CHECK を検証する（ADR-0052）。
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '5min';

ALTER TABLE studbook.breeding_result
VALIDATE CONSTRAINT chk_breeding_result_foaling_date;

ALTER TABLE studbook.breeding_result
VALIDATE CONSTRAINT chk_breeding_result_outcome_covering;
