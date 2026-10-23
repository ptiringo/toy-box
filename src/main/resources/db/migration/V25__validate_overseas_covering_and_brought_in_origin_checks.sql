-- V24 で NOT VALID として張り直した種付・出自の CHECK を検証する（ADR-0052）。
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '5min';

ALTER TABLE studbook.breeding_result
VALIDATE CONSTRAINT chk_breeding_result_covering;

ALTER TABLE studbook.blood_horse
VALIDATE CONSTRAINT chk_blood_horse_origin;
